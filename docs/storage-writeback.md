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

WAL files use periodic background forcing for replication and recovery. Each
pass forces the whole current mapping, with no dirty ranges or dirty flag.
Writers serialize complete records, but do not hold their append locks during
background forcing. Saves return without waiting for that force; a write made
during a pass may require the next pass to become durable. The worker sleeps
20 ms between passes; that is not a guaranteed maximum durability delay.

The next WAL is reserved and forced in the background under a `.pending` name
that recovery ignores. Once it is ready, rotation renames it to the next numbered
`.wal`, switches writers to it, and queues final forcing, truncation, close, and
compression of the old file. If preparation is late, writers keep using the
current WAL: the 20 MiB rotation threshold is soft. No append waits for preparation
or background finalization. File creation, mapping, rename, and OS page faults
can still incur filesystem latency.

Mapping growth does not force data or wait for an active force. Old WAL mappings
remain alive until finalization; the larger replacement covers their contents.
Forcing also persists file metadata after creating or growing a mapping.
Explicit force, truncation, and shutdown still wait for their durability work.
Shutdown drains preparation and finalization, reports background errors, and
discards the unused prepared file. Reopen discards abandoned `.pending` files.
