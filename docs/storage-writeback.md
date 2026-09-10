# Data-file writeback and WAL forcing

Data and index files use operating-system writeback. `FileChannelStore`,
`MemoryMappedStore`, and their encrypted variants do not request explicit
forcing during writes, mapping growth, `commit()`, or close. Their mappings
also skip dirty-range tracking.

`commit()` still finishes allocation reservations and writes the logical end
of each store. A factory commit prepares a retirement generation, commits both
stores, and publishes that generation for slot reuse. These are logical
bookkeeping operations; they do not establish storage-device durability.
Close still releases mappings and truncates growth reservations to the logical
file size. Data and index files have no power-loss durability guarantee.

WAL files retain their existing forcing for replication and recovery. Their
`WholeFileMapping` instances explicitly enable forcing, including after mapping
creation and growth. Periodic WAL flushing, explicit force calls, rotation,
truncation, close, and compressed WAL publication retain their existing barriers.
Both initial WAL mappings and replacements after truncation enable forcing.
