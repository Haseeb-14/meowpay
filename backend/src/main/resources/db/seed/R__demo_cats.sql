insert into cats (name) values ('Mochi'), ('Luna'), ('Tofu'), ('Biscuit')
on conflict (name) do nothing;

insert into accounts (cat_id, balance)
select c.id,
       case c.name
           when 'Mochi'   then 500
           when 'Luna'    then 120
           when 'Biscuit' then 75
           else 0
       end
from cats c
where c.name in ('Mochi', 'Luna', 'Tofu', 'Biscuit')
  and not exists (select 1 from accounts a where a.cat_id = c.id);

insert into ledger_entries (account_id, kind, amount, balance_after)
select a.id, 'TOPUP', a.balance, a.balance
from accounts a
where a.balance > 0
  and not exists (select 1 from ledger_entries e where e.account_id = a.id);
