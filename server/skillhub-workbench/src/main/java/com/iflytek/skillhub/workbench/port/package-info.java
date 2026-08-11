/**
 * Framework-light workbench ports for later infrastructure adapters.
 *
 * <p>Adapters that persist workbench DB rows and workspace objects must provide an explicit
 * consistency strategy: transactional DB changes where possible, storage-write failure
 * compensation, or cleanup of orphaned workspace objects when a snapshot/event save fails.</p>
 */
package com.iflytek.skillhub.workbench.port;
