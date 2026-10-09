-- Apotek Naufal Farma: initial centralized multi-device / multi-branch schema.
-- Target: Supabase PostgreSQL. Apply only to a NEW backend project after review.
-- This migration does NOT touch the Android SQLite database or existing product data.
begin;

create extension if not exists pgcrypto;

create table if not exists public.organizations (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  created_at timestamptz not null default now()
);

insert into public.organizations (id, name)
values ('a0b9d2f4-61c2-4c65-9eab-000000000001', 'Apotek Naufal Farma')
on conflict (id) do nothing;

create table if not exists public.branches (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null references public.organizations(id) on delete restrict,
  code text not null,
  name text not null,
  address text,
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  unique (organization_id, code)
);

create table if not exists public.profiles (
  user_id uuid primary key references auth.users(id) on delete cascade,
  organization_id uuid not null references public.organizations(id) on delete restrict,
  branch_id uuid references public.branches(id) on delete restrict,
  role text not null check (role in ('owner','branch_admin','cashier','inventory')),
  display_name text not null default '',
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  check ((role = 'owner' and branch_id is null) or (role <> 'owner' and branch_id is not null))
);

create table if not exists public.products (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null references public.organizations(id) on delete restrict,
  code text,
  barcode text,
  name text not null,
  category text,
  brand text,
  unit text not null default 'pcs',
  is_active boolean not null default true,
  updated_at timestamptz not null default now(),
  unique (organization_id, code),
  unique (organization_id, barcode)
);

create table if not exists public.branch_inventory (
  branch_id uuid not null references public.branches(id) on delete restrict,
  product_id uuid not null references public.products(id) on delete restrict,
  quantity numeric(14,3) not null default 0 check (quantity >= 0),
  minimum_quantity numeric(14,3) not null default 0 check (minimum_quantity >= 0),
  purchase_price numeric(14,2) not null default 0 check (purchase_price >= 0),
  selling_price numeric(14,2) not null default 0 check (selling_price >= 0),
  updated_at timestamptz not null default now(),
  primary key (branch_id, product_id)
);

create table if not exists public.stock_movements (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null references public.organizations(id) on delete restrict,
  branch_id uuid not null references public.branches(id) on delete restrict,
  product_id uuid not null references public.products(id) on delete restrict,
  movement_type text not null check (movement_type in ('opening','receive','sale','return','adjustment','transfer_out','transfer_in','expired','damaged')),
  quantity_delta numeric(14,3) not null check (quantity_delta <> 0),
  reference_type text,
  reference_id uuid,
  idempotency_key text not null,
  note text,
  actor_user_id uuid references auth.users(id) on delete set null,
  created_at timestamptz not null default now(),
  unique (organization_id, idempotency_key)
);

create table if not exists public.sales (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null references public.organizations(id) on delete restrict,
  branch_id uuid not null references public.branches(id) on delete restrict,
  transaction_no text not null,
  status text not null default 'completed' check (status in ('pending_sync','completed','voided')),
  subtotal numeric(14,2) not null default 0 check (subtotal >= 0),
  discount numeric(14,2) not null default 0 check (discount >= 0),
  total numeric(14,2) not null default 0 check (total >= 0),
  idempotency_key text not null,
  actor_user_id uuid references auth.users(id) on delete set null,
  device_id uuid,
  sold_at timestamptz not null default now(),
  created_at timestamptz not null default now(),
  unique (branch_id, transaction_no),
  unique (organization_id, idempotency_key)
);

create table if not exists public.sale_items (
  id uuid primary key default gen_random_uuid(),
  sale_id uuid not null references public.sales(id) on delete restrict,
  product_id uuid not null references public.products(id) on delete restrict,
  product_name_snapshot text not null,
  quantity numeric(14,3) not null check (quantity > 0),
  unit_price numeric(14,2) not null check (unit_price >= 0),
  purchase_price_snapshot numeric(14,2) not null default 0 check (purchase_price_snapshot >= 0),
  line_total numeric(14,2) not null check (line_total >= 0)
);

create table if not exists public.stock_transfers (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null references public.organizations(id) on delete restrict,
  source_branch_id uuid not null references public.branches(id) on delete restrict,
  destination_branch_id uuid not null references public.branches(id) on delete restrict,
  status text not null default 'requested' check (status in ('requested','dispatched','received','cancelled')),
  transfer_no text not null,
  requested_by uuid references auth.users(id) on delete set null,
  dispatched_by uuid references auth.users(id) on delete set null,
  received_by uuid references auth.users(id) on delete set null,
  requested_at timestamptz not null default now(),
  dispatched_at timestamptz,
  received_at timestamptz,
  unique (organization_id, transfer_no),
  check (source_branch_id <> destination_branch_id)
);

create table if not exists public.stock_transfer_items (
  transfer_id uuid not null references public.stock_transfers(id) on delete restrict,
  product_id uuid not null references public.products(id) on delete restrict,
  quantity numeric(14,3) not null check (quantity > 0),
  primary key (transfer_id, product_id)
);

create table if not exists public.registered_devices (
  id uuid primary key default gen_random_uuid(),
  organization_id uuid not null references public.organizations(id) on delete restrict,
  branch_id uuid not null references public.branches(id) on delete restrict,
  device_label text not null,
  revoked_at timestamptz,
  last_seen_at timestamptz,
  created_at timestamptz not null default now()
);

create table if not exists public.audit_events (
  id bigint generated always as identity primary key,
  organization_id uuid not null references public.organizations(id) on delete restrict,
  branch_id uuid references public.branches(id) on delete restrict,
  actor_user_id uuid references auth.users(id) on delete set null,
  device_id uuid references public.registered_devices(id) on delete set null,
  event_type text not null,
  entity_type text,
  entity_id text,
  details jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

create index if not exists idx_inventory_product on public.branch_inventory(product_id);
create index if not exists idx_movements_branch_time on public.stock_movements(branch_id, created_at desc);
create index if not exists idx_sales_branch_time on public.sales(branch_id, sold_at desc);
create index if not exists idx_audit_org_time on public.audit_events(organization_id, created_at desc);

-- Helper functions use a fixed search_path to avoid search-path injection.
create or replace function public.current_profile()
returns public.profiles
language sql stable security definer
set search_path = public
as $$
  select p from public.profiles p
  where p.user_id = auth.uid() and p.is_active = true
  limit 1
$$;

create or replace function public.can_access_branch(target_branch uuid)
returns boolean
language sql stable security definer
set search_path = public
as $$
  select exists (
    select 1
    from public.profiles p
    join public.branches b on b.organization_id = p.organization_id
    where p.user_id = auth.uid()
      and p.is_active = true
      and b.id = target_branch
      and b.is_active = true
      and (p.role = 'owner' or p.branch_id = target_branch)
  )
$$;

create or replace function public.is_org_owner(target_org uuid)
returns boolean
language sql stable security definer
set search_path = public
as $$
  select exists (
    select 1 from public.profiles p
    where p.user_id = auth.uid()
      and p.is_active = true
      and p.role = 'owner'
      and p.organization_id = target_org
  )
$$;

alter table public.organizations enable row level security;
alter table public.branches enable row level security;
alter table public.profiles enable row level security;
alter table public.products enable row level security;
alter table public.branch_inventory enable row level security;
alter table public.stock_movements enable row level security;
alter table public.sales enable row level security;
alter table public.sale_items enable row level security;
alter table public.stock_transfers enable row level security;
alter table public.stock_transfer_items enable row level security;
alter table public.registered_devices enable row level security;
alter table public.audit_events enable row level security;

create policy "org members read organization" on public.organizations
for select using (id = (select (public.current_profile()).organization_id));

create policy "members read active branches" on public.branches
for select using (
  organization_id = (select (public.current_profile()).organization_id)
  and (public.is_org_owner(organization_id) or id = (select (public.current_profile()).branch_id))
);
create policy "owner manages branches" on public.branches
for all using (public.is_org_owner(organization_id))
with check (public.is_org_owner(organization_id));

create policy "users read permitted profiles" on public.profiles
for select using (
  user_id = auth.uid()
  or public.is_org_owner(organization_id)
  or (branch_id = (select (public.current_profile()).branch_id)
      and organization_id = (select (public.current_profile()).organization_id))
);
create policy "owner manages profiles" on public.profiles
for all using (public.is_org_owner(organization_id))
with check (public.is_org_owner(organization_id));

create policy "members read products" on public.products
for select using (organization_id = (select (public.current_profile()).organization_id));
create policy "owner and branch admin manage products" on public.products
for all using (
  organization_id = (select (public.current_profile()).organization_id)
  and (public.is_org_owner(organization_id) or
       ((select (public.current_profile()).role) = 'branch_admin'
        and (select (public.current_profile()).branch_id) is not null))
)
with check (
  organization_id = (select (public.current_profile()).organization_id)
  and (public.is_org_owner(organization_id) or
       ((select (public.current_profile()).role) = 'branch_admin'
        and (select (public.current_profile()).branch_id) is not null))
);

create policy "branch users read inventory" on public.branch_inventory
for select using (public.can_access_branch(branch_id));
create policy "authorized staff update inventory" on public.branch_inventory
for all using (
  public.can_access_branch(branch_id)
  and ((select (public.current_profile()).role) in ('owner','branch_admin','inventory'))
)
with check (
  public.can_access_branch(branch_id)
  and ((select (public.current_profile()).role) in ('owner','branch_admin','inventory'))
  and exists (select 1 from public.products p
    where p.id = product_id
      and p.organization_id = (select (public.current_profile()).organization_id))
);

create policy "branch users read movements" on public.stock_movements
for select using (public.can_access_branch(branch_id));
create policy "authorized staff add movements" on public.stock_movements
for insert with check (
  public.can_access_branch(branch_id)
  and organization_id = (select (public.current_profile()).organization_id)
  and ((select (public.current_profile()).role) in ('owner','branch_admin','inventory','cashier'))
  and (actor_user_id is null or actor_user_id = auth.uid())
  and (((select (public.current_profile()).role) <> 'cashier') or movement_type in ('sale','return'))
  and exists (select 1 from public.products p
    where p.id = product_id and p.organization_id = stock_movements.organization_id)
);

create policy "branch users read sales" on public.sales
for select using (public.can_access_branch(branch_id));
create policy "branch staff create sales" on public.sales
for insert with check (
  public.can_access_branch(branch_id)
  and organization_id = (select (public.current_profile()).organization_id)
  and ((select (public.current_profile()).role) in ('owner','branch_admin','cashier'))
  and (actor_user_id is null or actor_user_id = auth.uid())
  and (status <> 'voided' or (select (public.current_profile()).role) in ('owner','branch_admin'))
);
create policy "owner or branch admin void sales" on public.sales
for update using (
  public.can_access_branch(branch_id)
  and ((select (public.current_profile()).role) in ('owner','branch_admin'))
)
with check (
  public.can_access_branch(branch_id)
  and ((select (public.current_profile()).role) in ('owner','branch_admin'))
);

create policy "read sale items through sale scope" on public.sale_items
for select using (exists (select 1 from public.sales s where s.id = sale_id and public.can_access_branch(s.branch_id)));
create policy "create sale items through sale scope" on public.sale_items
for insert with check (
  exists (select 1 from public.sales s
    where s.id = sale_id
      and public.can_access_branch(s.branch_id)
      and s.organization_id = (select (public.current_profile()).organization_id)
      and ((select (public.current_profile()).role) in ('owner','branch_admin','cashier')))
  and exists (select 1 from public.products p
    where p.id = product_id
      and p.organization_id = (select (public.current_profile()).organization_id))
);

create policy "branch users read transfers" on public.stock_transfers
for select using (public.can_access_branch(source_branch_id) or public.can_access_branch(destination_branch_id));
create policy "staff request transfers" on public.stock_transfers
for insert with check (
  (public.can_access_branch(source_branch_id) or public.can_access_branch(destination_branch_id))
  and organization_id = (select (public.current_profile()).organization_id)
  and exists (select 1 from public.branches src
    join public.branches dst on dst.organization_id = src.organization_id
    where src.id = source_branch_id and dst.id = destination_branch_id
      and src.organization_id = stock_transfers.organization_id)
  and ((select (public.current_profile()).role) in ('owner','branch_admin','inventory'))
);
create policy "authorized staff progress transfers" on public.stock_transfers
for update using (
  (public.can_access_branch(source_branch_id) or public.can_access_branch(destination_branch_id))
  and organization_id = (select (public.current_profile()).organization_id)
  and ((select (public.current_profile()).role) in ('owner','branch_admin','inventory'))
)
with check (
  (public.can_access_branch(source_branch_id) or public.can_access_branch(destination_branch_id))
  and organization_id = (select (public.current_profile()).organization_id)
  and exists (select 1 from public.branches src
    join public.branches dst on dst.organization_id = src.organization_id
    where src.id = source_branch_id and dst.id = destination_branch_id
      and src.organization_id = stock_transfers.organization_id)
  and ((select (public.current_profile()).role) in ('owner','branch_admin','inventory'))
);
create policy "read transfer items by transfer scope" on public.stock_transfer_items
for select using (exists (select 1 from public.stock_transfers t where t.id = transfer_id
  and (public.can_access_branch(t.source_branch_id) or public.can_access_branch(t.destination_branch_id))));
create policy "write transfer items by transfer scope" on public.stock_transfer_items
for all using (exists (select 1 from public.stock_transfers t where t.id = transfer_id
  and (public.can_access_branch(t.source_branch_id) or public.can_access_branch(t.destination_branch_id))
  and ((select (public.current_profile()).role) in ('owner','branch_admin','inventory'))))
with check (exists (select 1 from public.stock_transfers t where t.id = transfer_id
  and (public.can_access_branch(t.source_branch_id) or public.can_access_branch(t.destination_branch_id))
  and ((select (public.current_profile()).role) in ('owner','branch_admin','inventory'))));

create policy "owner manages devices" on public.registered_devices
for all using (public.is_org_owner(organization_id))
with check (public.is_org_owner(organization_id));
create policy "branch admins see branch devices" on public.registered_devices
for select using (public.can_access_branch(branch_id) and (select (public.current_profile()).role) = 'branch_admin');

create or replace function public.owner_sale_item_costs(target_sale uuid)
returns table (sale_item_id uuid, sale_id uuid, purchase_price_snapshot numeric)
language sql stable security definer
set search_path = public
as $
  select si.id, si.sale_id, si.purchase_price_snapshot
  from public.sale_items si
  join public.sales s on s.id = si.sale_id
  join public.profiles p on p.user_id = auth.uid()
  where s.id = target_sale
    and p.is_active = true
    and p.role = 'owner'
    and p.organization_id = s.organization_id
$;

create policy "read audit within scope" on public.audit_events
for select using (
  public.is_org_owner(organization_id)
  or (branch_id is not null and public.can_access_branch(branch_id)
      and (select (public.current_profile()).role) = 'branch_admin')
);
create policy "authenticated users append audit events" on public.audit_events
for insert with check (
  organization_id = (select (public.current_profile()).organization_id)
  and (actor_user_id is null or actor_user_id = auth.uid())
  and (branch_id is null or public.can_access_branch(branch_id))
);

-- Do not grant direct table access to anonymous clients. Supabase grants to authenticated
-- are still filtered by RLS; privileged service-role keys must remain server-side only.
grant usage on schema public to authenticated;
grant select, insert, update, delete on public.branches, public.profiles, public.products,
  public.branch_inventory, public.stock_movements, public.sales,
  public.stock_transfers, public.stock_transfer_items, public.registered_devices,
  public.audit_events to authenticated;
-- Do not grant table-level SELECT/INSERT on sale_items: purchase-cost snapshots are
-- restricted to an owner-only function; client inserts cannot set that sensitive column.
revoke all on public.sale_items from authenticated;
grant select (id, sale_id, product_id, product_name_snapshot, quantity, unit_price, line_total)
  on public.sale_items to authenticated;
grant insert (sale_id, product_id, product_name_snapshot, quantity, unit_price, line_total)
  on public.sale_items to authenticated;
grant execute on function public.owner_sale_item_costs(uuid) to authenticated;
grant select on public.organizations to authenticated;
grant usage, select on sequence public.audit_events_id_seq to authenticated;

commit;
