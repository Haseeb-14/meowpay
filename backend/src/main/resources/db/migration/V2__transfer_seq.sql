alter table transfers add column seq bigserial not null;
create unique index transfers_seq_idx on transfers (seq);
