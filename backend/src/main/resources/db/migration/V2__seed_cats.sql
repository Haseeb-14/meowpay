-- Demo data. Humans topping up wallets is outside this slice, so the opening
-- balances are seeded here as TOPUP ledger entries. Tofu starts at zero on
-- purpose so the insufficient-funds path is one click away.
insert into cats (name) values ('Mochi'), ('Luna'), ('Tofu'), ('Biscuit');

insert into accounts (cat_id, balance)
select id,
       case name
           when 'Mochi'   then 500
           when 'Luna'    then 120
           when 'Biscuit' then 75
           else 0
       end
from cats;

insert into ledger_entries (account_id, kind, amount, balance_after)
select id, 'TOPUP', balance, balance
from accounts
where balance > 0;
