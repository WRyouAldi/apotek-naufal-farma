-- Read-only stock summary view. security_invoker keeps underlying RLS restrictions:
-- owner sees the organization total; branch-scoped users see only their permitted rows.
begin;

create or replace view public.organization_stock_summary
with (security_invoker = true)
as
select
  p.organization_id,
  p.id as product_id,
  p.code,
  p.barcode,
  p.name as product_name,
  coalesce(sum(i.quantity), 0)::numeric(14,3) as visible_stock_quantity,
  count(i.branch_id)::integer as visible_branch_count
from public.products p
left join public.branch_inventory i on i.product_id = p.id
group by p.organization_id, p.id, p.code, p.barcode, p.name;

grant select on public.organization_stock_summary to authenticated;

commit;
