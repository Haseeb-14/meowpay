-- A monotonic sequence number for ordering. created_at comes from the app server's
-- clock, so two transfers in the same instant (or from two servers with skewed
-- clocks) had no stable order; seq is assigned by the database in commit-ish order.
alter table transfers add column seq bigserial not null;
create unique index transfers_seq_idx on transfers (seq);
