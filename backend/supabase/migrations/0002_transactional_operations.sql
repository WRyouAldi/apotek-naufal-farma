-- Transaction-safe business operations for the centralized multi-branch POS.
-- Apply only after 0001_multi_branch_core.sql has been reviewed and tested in staging.
begin;

-- Sale creation and stock decrement are atomic. Clients cannot choose purchase cost or
-- override catalog prices; both are read from the branch inventory row.
create or replace function public.checkout_sale(
  p_branch_id uuid,
  p_transaction_no text,
  p_idempotency_key text,
  p_items jsonb,
  p_discount numeric default 0
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_profile public.profiles%rowtype;
  v_org uuid;
  v_sale_id uuid;
  v_existing uuid;
  v_item jsonb;
  v_product uuid;
  v_qty numeric(14,3);
  v_price numeric(14,2);
  v_cost numeric(14,2);
  v_name text;
  v_subtotal numeric(14,2) := 0;
  v_total numeric(14,2);
  v_line numeric(14,2);
begin
  select * into v_profile from public.profiles
  where user_id = auth.uid() and is_active = true;
  if not found then raise exception 'Active user profile required'; end if;
  if v_profile.role not in ('owner','branch_admin','cashier') then
    raise exception 'Role cannot perform checkout';
  end if;
  if not public.can_access_branch(p_branch_id) then
    raise exception 'Branch access denied';
  end if;
  select organization_id into v_org from public.branches
  where id = p_branch_id and is_active = true;
  if v_org is null or v_org <> v_profile.organization_id then
    raise exception 'Invalid branch';
  end if;
  if coalesce(trim(p_transaction_no),'') = '' or coalesce(trim(p_idempotency_key),'') = '' then
    raise exception 'Transaction number and idempotency key are required';
  end if;
  if jsonb_typeof(p_items) <> 'array' or jsonb_array_length(p_items) = 0 then
    raise exception 'At least one sale item is required';
  end if;
  if exists (
    select 1 from jsonb_array_elements(p_items) x
    group by x->>'product_id' having count(*) > 1
  ) then raise exception 'Duplicate product lines are not allowed; combine quantities first'; end if;
  if p_discount is null or p_discount < 0 then raise exception 'Invalid discount'; end if;

  select id into v_existing from public.sales
  where organization_id = v_org and idempotency_key = p_idempotency_key;
  if v_existing is not null then return v_existing; end if;

  v_sale_id := gen_random_uuid();
  insert into public.sales (
    id, organization_id, branch_id, transaction_no, status,
    subtotal, discount, total, idempotency_key, actor_user_id
  ) values (
    v_sale_id, v_org, p_branch_id, p_transaction_no, 'completed',
    0, p_discount, 0, p_idempotency_key, auth.uid()
  );

  for v_item in select value from jsonb_array_elements(p_items)
  loop
    v_product := (v_item->>'product_id')::uuid;
    v_qty := (v_item->>'quantity')::numeric;
    if v_product is null or v_qty is null or v_qty <= 0 then
      raise exception 'Invalid product or quantity in sale';
    end if;

    select p.name, i.selling_price, i.purchase_price
      into v_name, v_price, v_cost
    from public.branch_inventory i
    join public.products p on p.id = i.product_id
    where i.branch_id = p_branch_id
      and i.product_id = v_product
      and p.organization_id = v_org
      and p.is_active = true
    for update of i;

    if not found then raise exception 'Product is not configured for this branch: %', v_product; end if;
    if (select quantity from public.branch_inventory
        where branch_id = p_branch_id and product_id = v_product) < v_qty then
      raise exception 'Insufficient stock for product: %', v_name;
    end if;

    v_line := round(v_qty * v_price, 2);
    v_subtotal := v_subtotal + v_line;

    insert into public.sale_items (
      sale_id, product_id, product_name_snapshot, quantity,
      unit_price, purchase_price_snapshot, line_total
    ) values (v_sale_id, v_product, v_name, v_qty, v_price, v_cost, v_line);

    update public.branch_inventory
      set quantity = quantity - v_qty, updated_at = now()
      where branch_id = p_branch_id and product_id = v_product;

    insert into public.stock_movements (
      organization_id, branch_id, product_id, movement_type, quantity_delta,
      reference_type, reference_id, idempotency_key, actor_user_id
    ) values (
      v_org, p_branch_id, v_product, 'sale', -v_qty,
      'sale', v_sale_id, 'sale:' || v_sale_id::text || ':' || v_product::text, auth.uid()
    );
  end loop;

  if p_discount > v_subtotal then raise exception 'Discount cannot exceed subtotal'; end if;
  v_total := v_subtotal - p_discount;
  update public.sales set subtotal = v_subtotal, total = v_total where id = v_sale_id;

  insert into public.audit_events (
    organization_id, branch_id, actor_user_id, event_type, entity_type, entity_id, details
  ) values (
    v_org, p_branch_id, auth.uid(), 'sale.completed', 'sale', v_sale_id::text,
    jsonb_build_object('transaction_no', p_transaction_no, 'subtotal', v_subtotal,
                       'discount', p_discount, 'total', v_total)
  );
  return v_sale_id;
end;
$$;

-- Receive stock from suppliers or record a controlled stock correction.
-- Opening/receipt/adjustment operations require an inventory-capable role and idempotency.
create or replace function public.record_stock_movement(
  p_branch_id uuid,
  p_product_id uuid,
  p_movement_type text,
  p_quantity_delta numeric,
  p_idempotency_key text,
  p_note text default null,
  p_purchase_price numeric default null,
  p_selling_price numeric default null
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_profile public.profiles%rowtype;
  v_org uuid;
  v_movement_id uuid;
  v_qty numeric(14,3);
begin
  select * into v_profile from public.profiles
  where user_id = auth.uid() and is_active = true;
  if not found then raise exception 'Active user profile required'; end if;
  if v_profile.role not in ('owner','branch_admin','inventory') then
    raise exception 'Role cannot adjust or receive stock';
  end if;
  if not public.can_access_branch(p_branch_id) then raise exception 'Branch access denied'; end if;
  if p_movement_type not in ('opening','receive','return','adjustment','expired','damaged') then
    raise exception 'Unsupported stock movement type';
  end if;
  if p_movement_type in ('opening','receive','return') and p_quantity_delta <= 0 then
    raise exception 'Opening, receive and return movements must add stock';
  end if;
  if p_movement_type in ('expired','damaged') and p_quantity_delta >= 0 then
    raise exception 'Expired and damaged movements must remove stock';
  end if;
  if p_purchase_price is not null and p_purchase_price < 0
     or p_selling_price is not null and p_selling_price < 0 then
    raise exception 'Prices cannot be negative';
  end if;
  if p_quantity_delta is null or p_quantity_delta = 0 or coalesce(trim(p_idempotency_key),'') = '' then
    raise exception 'Non-zero quantity and idempotency key are required';
  end if;
  select organization_id into v_org from public.branches
    where id = p_branch_id and is_active = true;
  if v_org is null or v_org <> v_profile.organization_id then raise exception 'Invalid branch'; end if;
  if not exists (select 1 from public.products where id = p_product_id
                 and organization_id = v_org and is_active = true) then
    raise exception 'Product not found in organization';
  end if;

  select id into v_movement_id from public.stock_movements
    where organization_id = v_org and idempotency_key = p_idempotency_key;
  if v_movement_id is not null then return v_movement_id; end if;

  insert into public.branch_inventory (branch_id, product_id, quantity)
    values (p_branch_id, p_product_id, 0)
    on conflict (branch_id, product_id) do nothing;
  select quantity into v_qty from public.branch_inventory
    where branch_id = p_branch_id and product_id = p_product_id for update;

  if v_qty + p_quantity_delta < 0 then raise exception 'Stock cannot become negative'; end if;

  update public.branch_inventory set
    quantity = quantity + p_quantity_delta,
    purchase_price = coalesce(p_purchase_price, purchase_price),
    selling_price = coalesce(p_selling_price, selling_price),
    updated_at = now()
  where branch_id = p_branch_id and product_id = p_product_id;

  insert into public.stock_movements (
    organization_id, branch_id, product_id, movement_type, quantity_delta,
    idempotency_key, note, actor_user_id
  ) values (
    v_org, p_branch_id, p_product_id, p_movement_type, p_quantity_delta,
    p_idempotency_key, p_note, auth.uid()
  ) returning id into v_movement_id;

  insert into public.audit_events (
    organization_id, branch_id, actor_user_id, event_type, entity_type, entity_id, details
  ) values (
    v_org, p_branch_id, auth.uid(), 'stock.movement', 'stock_movement', v_movement_id::text,
    jsonb_build_object('product_id', p_product_id, 'type', p_movement_type,
                       'quantity_delta', p_quantity_delta, 'note', p_note)
  );
  return v_movement_id;
end;
$$;

-- Create a transfer request. It does not change either branch's balance.
create or replace function public.request_stock_transfer(
  p_transfer_no text,
  p_source_branch_id uuid,
  p_destination_branch_id uuid,
  p_items jsonb
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
  v_profile public.profiles%rowtype;
  v_org uuid;
  v_transfer_id uuid;
  v_item jsonb;
  v_product uuid;
  v_qty numeric(14,3);
begin
  select * into v_profile from public.profiles
  where user_id = auth.uid() and is_active = true;
  if not found or v_profile.role not in ('owner','branch_admin','inventory') then
    raise exception 'Inventory permission required';
  end if;
  if not public.can_access_branch(p_source_branch_id) then raise exception 'Source branch access denied'; end if;
  select organization_id into v_org from public.branches
    where id = p_source_branch_id and is_active = true;
  if v_org is null or v_org <> v_profile.organization_id
     or not exists (select 1 from public.branches where id = p_destination_branch_id
                    and organization_id = v_org and is_active = true)
     or p_source_branch_id = p_destination_branch_id then
    raise exception 'Invalid transfer branches';
  end if;
  if coalesce(trim(p_transfer_no),'') = '' or jsonb_typeof(p_items) <> 'array'
     or jsonb_array_length(p_items) = 0 then raise exception 'Transfer number and items are required'; end if;
  if exists (
    select 1 from jsonb_array_elements(p_items) x
    group by x->>'product_id' having count(*) > 1
  ) then raise exception 'Duplicate transfer product lines are not allowed'; end if;

  insert into public.stock_transfers (
    organization_id, source_branch_id, destination_branch_id, transfer_no, requested_by
  ) values (v_org, p_source_branch_id, p_destination_branch_id, p_transfer_no, auth.uid())
  returning id into v_transfer_id;

  for v_item in select value from jsonb_array_elements(p_items)
  loop
    v_product := (v_item->>'product_id')::uuid;
    v_qty := (v_item->>'quantity')::numeric;
    if v_product is null or v_qty is null or v_qty <= 0
      or not exists (select 1 from public.products where id = v_product and organization_id = v_org) then
      raise exception 'Invalid transfer item';
    end if;
    insert into public.stock_transfer_items(transfer_id, product_id, quantity)
      values (v_transfer_id, v_product, v_qty);
  end loop;

  insert into public.audit_events (
    organization_id, branch_id, actor_user_id, event_type, entity_type, entity_id, details
  ) values (
    v_org, p_source_branch_id, auth.uid(), 'transfer.requested', 'stock_transfer', v_transfer_id::text,
    jsonb_build_object('destination_branch_id', p_destination_branch_id, 'transfer_no', p_transfer_no)
  );
  return v_transfer_id;
end;
$$;

-- Dispatch removes quantities from source stock and records an immutable ledger event.
create or replace function public.dispatch_stock_transfer(p_transfer_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_profile public.profiles%rowtype;
  v_transfer public.stock_transfers%rowtype;
  v_item record;
  v_qty numeric(14,3);
begin
  select * into v_profile from public.profiles where user_id = auth.uid() and is_active = true;
  if not found or v_profile.role not in ('owner','branch_admin','inventory') then raise exception 'Inventory permission required'; end if;
  select * into v_transfer from public.stock_transfers where id = p_transfer_id for update;
  if not found or v_transfer.organization_id <> v_profile.organization_id then raise exception 'Transfer not found'; end if;
  if not public.can_access_branch(v_transfer.source_branch_id) then raise exception 'Source branch access denied'; end if;
  if v_transfer.status <> 'requested' then raise exception 'Transfer is not awaiting dispatch'; end if;

  for v_item in select product_id, quantity from public.stock_transfer_items
    where transfer_id = p_transfer_id order by product_id
  loop
    select quantity into v_qty from public.branch_inventory
      where branch_id = v_transfer.source_branch_id and product_id = v_item.product_id for update;
    if not found or v_qty < v_item.quantity then raise exception 'Insufficient source stock for product %', v_item.product_id; end if;
    update public.branch_inventory set quantity = quantity - v_item.quantity, updated_at = now()
      where branch_id = v_transfer.source_branch_id and product_id = v_item.product_id;
    insert into public.stock_movements (
      organization_id, branch_id, product_id, movement_type, quantity_delta,
      reference_type, reference_id, idempotency_key, actor_user_id
    ) values (
      v_transfer.organization_id, v_transfer.source_branch_id, v_item.product_id, 'transfer_out', -v_item.quantity,
      'stock_transfer', p_transfer_id, 'transfer-out:' || p_transfer_id::text || ':' || v_item.product_id::text, auth.uid()
    );
  end loop;
  update public.stock_transfers set status = 'dispatched', dispatched_by = auth.uid(), dispatched_at = now()
    where id = p_transfer_id;
  insert into public.audit_events(organization_id, branch_id, actor_user_id, event_type, entity_type, entity_id)
    values (v_transfer.organization_id, v_transfer.source_branch_id, auth.uid(), 'transfer.dispatched', 'stock_transfer', p_transfer_id::text);
end;
$$;

-- Receive adds quantities only at destination. A transfer request/dispatch never pre-adds destination stock.
create or replace function public.receive_stock_transfer(p_transfer_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_profile public.profiles%rowtype;
  v_transfer public.stock_transfers%rowtype;
  v_item record;
begin
  select * into v_profile from public.profiles where user_id = auth.uid() and is_active = true;
  if not found or v_profile.role not in ('owner','branch_admin','inventory') then raise exception 'Inventory permission required'; end if;
  select * into v_transfer from public.stock_transfers where id = p_transfer_id for update;
  if not found or v_transfer.organization_id <> v_profile.organization_id then raise exception 'Transfer not found'; end if;
  if not public.can_access_branch(v_transfer.destination_branch_id) then raise exception 'Destination branch access denied'; end if;
  if v_transfer.status <> 'dispatched' then raise exception 'Transfer is not awaiting receipt'; end if;

  for v_item in select product_id, quantity from public.stock_transfer_items
    where transfer_id = p_transfer_id order by product_id
  loop
    insert into public.branch_inventory(
      branch_id, product_id, quantity, minimum_quantity, purchase_price, selling_price
    )
    select v_transfer.destination_branch_id, src.product_id, 0,
           src.minimum_quantity, src.purchase_price, src.selling_price
    from public.branch_inventory src
    where src.branch_id = v_transfer.source_branch_id and src.product_id = v_item.product_id
    on conflict (branch_id, product_id) do nothing;
    update public.branch_inventory set quantity = quantity + v_item.quantity, updated_at = now()
      where branch_id = v_transfer.destination_branch_id and product_id = v_item.product_id;
    insert into public.stock_movements (
      organization_id, branch_id, product_id, movement_type, quantity_delta,
      reference_type, reference_id, idempotency_key, actor_user_id
    ) values (
      v_transfer.organization_id, v_transfer.destination_branch_id, v_item.product_id, 'transfer_in', v_item.quantity,
      'stock_transfer', p_transfer_id, 'transfer-in:' || p_transfer_id::text || ':' || v_item.product_id::text, auth.uid()
    );
  end loop;
  update public.stock_transfers set status = 'received', received_by = auth.uid(), received_at = now()
    where id = p_transfer_id;
  insert into public.audit_events(organization_id, branch_id, actor_user_id, event_type, entity_type, entity_id)
    values (v_transfer.organization_id, v_transfer.destination_branch_id, auth.uid(), 'transfer.received', 'stock_transfer', p_transfer_id::text);
end;
$$;

-- Clients must use the transaction-safe functions rather than bypassing the stock ledger.
revoke insert, update, delete on public.sales from authenticated;
revoke insert, update, delete on public.sale_items from authenticated;
revoke insert (sale_id, product_id, product_name_snapshot, quantity, unit_price, line_total)
  on public.sale_items from authenticated;
revoke insert, update, delete on public.audit_events from authenticated;
revoke insert, update, delete on public.stock_movements from authenticated;
revoke insert, update, delete on public.branch_inventory from authenticated;
revoke insert, update, delete on public.stock_transfers from authenticated;
revoke insert, update, delete on public.stock_transfer_items from authenticated;
grant execute on function public.checkout_sale(uuid,text,text,jsonb,numeric) to authenticated;
grant execute on function public.record_stock_movement(uuid,uuid,text,numeric,text,text,numeric,numeric) to authenticated;
grant execute on function public.request_stock_transfer(text,uuid,uuid,jsonb) to authenticated;
grant execute on function public.dispatch_stock_transfer(uuid) to authenticated;
grant execute on function public.receive_stock_transfer(uuid) to authenticated;

commit;
