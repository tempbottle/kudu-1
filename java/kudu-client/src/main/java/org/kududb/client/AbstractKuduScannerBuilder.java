// Copyright (c) 2015, Cloudera, inc.
// Confidential Cloudera Information: Covered by NDA.
package org.kududb.client;

import java.util.ArrayList;
import java.util.List;

import com.google.common.collect.ImmutableList;
import org.kududb.annotations.InterfaceAudience;
import org.kududb.annotations.InterfaceStability;
import org.kududb.tserver.Tserver;

/**
 * Abstract class to extend in order to create builders for scanners.
 */
@InterfaceAudience.Public
@InterfaceStability.Evolving
public abstract class AbstractKuduScannerBuilder
    <S extends AbstractKuduScannerBuilder<? super S, T>, T> {
  final AsyncKuduClient client;
  final KuduTable table;
  final List<Tserver.ColumnRangePredicatePB> columnRangePredicates;

  AsyncKuduScanner.ReadMode readMode = AsyncKuduScanner.ReadMode.READ_LATEST;
  int maxNumBytes = 1024*1024;
  long limit = Long.MAX_VALUE;
  boolean prefetching = false;
  boolean cacheBlocks = true;
  long htTimestamp = AsyncKuduClient.NO_TIMESTAMP;
  byte[] lowerBoundPrimaryKey = AsyncKuduClient.EMPTY_ARRAY;
  byte[] upperBoundPrimaryKey = AsyncKuduClient.EMPTY_ARRAY;
  byte[] lowerBoundPartitionKey = AsyncKuduClient.EMPTY_ARRAY;
  byte[] upperBoundPartitionKey = AsyncKuduClient.EMPTY_ARRAY;
  List<String> projectedColumnNames = null;
  long scanRequestTimeout;

  AbstractKuduScannerBuilder(AsyncKuduClient client, KuduTable table) {
    this.client = client;
    this.table = table;
    this.columnRangePredicates = new ArrayList<>();
    this.scanRequestTimeout = client.getDefaultOperationTimeoutMs();
  }

  /**
   * Sets the read mode, the default is to read the latest values.
   * @param readMode a read mode for the scanner
   * @return this instance
   */
  public S readMode(AsyncKuduScanner.ReadMode readMode) {
    this.readMode = readMode;
    return (S) this;
  }

  /**
   * Adds a predicate for a column.
   * @param predicate predicate for a column to add
   * @return this instance
   */
  public S addColumnRangePredicate(ColumnRangePredicate predicate) {
    columnRangePredicates.add(predicate.getPb());
    return (S) this;
  }

  /**
   * Adds a list of predicates in their raw format,
   * as given by {@link ColumnRangePredicate#toByteArray(List)}.
   * @param predicateBytes predicates to add
   * @return this instance
   * @throws IllegalArgumentException thrown when the passed bytes aren't valid
   */
  public S addColumnRangePredicatesRaw(byte[] predicateBytes) {
    List<Tserver.ColumnRangePredicatePB> predicates =
        ColumnRangePredicate.fromByteArray(predicateBytes);
    columnRangePredicates.addAll(predicates);
    return (S) this;
  }

  /**
   * Set which columns will be read by the Scanner.
   * @param columnNames the names of columns to read, or 'null' to read all columns
   * (the default)
   */
  public S setProjectedColumnNames(List<String> columnNames) {
    if (columnNames != null) {
      projectedColumnNames = ImmutableList.copyOf(columnNames);
    } else {
      projectedColumnNames = null;
    }
    return (S) this;
  }

  /**
   * Sets the maximum number of bytes returned at once by the scanner. The default is 1MB.
   * <p>
   * Kudu may actually return more than this many bytes because it will not
   * truncate a rowResult in the middle.
   * @param maxNumBytes a strictly positive number of bytes
   * @return this instance
   */
  public S maxNumBytes(int maxNumBytes) {
    this.maxNumBytes = maxNumBytes;
    return (S) this;
  }

  /**
   * Sets a limit on the number of rows that will be returned by the scanner. There's no limit
   * by default.
   * @param limit a positive long
   * @return this instance
   */
  public S limit(long limit) {
    this.limit = limit;
    return (S) this;
  }

  /**
   * Enables prefetching of rows for the scanner, disabled by default.
   * @param prefetching a boolean that indicates if the scanner should prefetch rows
   * @return this instance
   */
  public S prefetching(boolean prefetching) {
    this.prefetching = prefetching;
    return (S) this;
  }

  /**
   * Sets the block caching policy for the scanner. If true, scanned data blocks will be cached
   * in memory and made available for future scans. Enabled by default.
   * @param cacheBlocks a boolean that indicates if data blocks should be cached or not
   * @return this instance
   */
  public S cacheBlocks(boolean cacheBlocks) {
    this.cacheBlocks = cacheBlocks;
    return (S) this;
  }

  /**
   * Sets a previously encoded HT timestamp as a snapshot timestamp, for tests. None is used by
   * default.
   * @param htTimestamp a long representing a HybridClock-encoded timestamp
   * @return this instance
   * @throws IllegalArgumentException if the timestamp is less than 0
   */
  @InterfaceAudience.Private
  public S snapshotTimestamp(long htTimestamp) {
    this.htTimestamp = htTimestamp;
    return (S) this;
  }

  /**
   * Sets how long each scan request to a server can last.
   * Defaults to {@link KuduClient#getDefaultOperationTimeoutMs()}.
   * @param scanRequestTimeout a long representing time in milliseconds
   * @return this instance
   */
  public S scanRequestTimeout(long scanRequestTimeout) {
    this.scanRequestTimeout = scanRequestTimeout;
    return (S) this;
  }

  /**
   * Add a lower bound (inclusive) primary key for the scan.
   * If any bound is already added, this bound is intersected with that one.
   * @param partialRow a partial row with specified key columns
   * @return this instance
   */
  public S lowerBound(PartialRow partialRow) {
    return lowerBoundRaw(partialRow.encodePrimaryKey());
  }

  /**
   * Like lowerBoundPrimaryKey() but the encoded primary key is an opaque byte array obtained elsewhere.
   * @param startPrimaryKey bytes containing an encoded start key
   * @return this instance
   * @deprecated use {@link #lowerBound(PartialRow)}
   */
  @Deprecated
  public S lowerBoundRaw(byte[] startPrimaryKey) {
    if (lowerBoundPrimaryKey == AsyncKuduClient.EMPTY_ARRAY ||
        Bytes.memcmp(startPrimaryKey, lowerBoundPrimaryKey) > 0) {
      this.lowerBoundPrimaryKey = startPrimaryKey;
    }
    return (S) this;
  }

  /**
   * Add an upper bound (exclusive) primary key for the scan.
   * If any bound is already added, this bound is intersected with that one.
   * @param partialRow a partial row with specified key columns
   * @return this instance
   */
  public S exclusiveUpperBound(PartialRow partialRow) {
    return exclusiveUpperBoundRaw(partialRow.encodePrimaryKey());
  }

  /**
   * Like exclusiveUpperBound() but the encoded primary key is an opaque byte array obtained elsewhere.
   * @param endPrimaryKey bytes containing an encoded end key
   * @return this instance
   * @deprecated use {@link #exclusiveUpperBound(PartialRow)}
   */
  @Deprecated
  public S exclusiveUpperBoundRaw(byte[] endPrimaryKey) {
    if (upperBoundPrimaryKey == AsyncKuduClient.EMPTY_ARRAY ||
        Bytes.memcmp(endPrimaryKey, upperBoundPrimaryKey) < 0) {
      this.upperBoundPrimaryKey = endPrimaryKey;
    }
    return (S) this;
  }

  /**
   * Set an encoded (inclusive) start partition key for the scan.
   *
   * @param partitionKey the encoded partition key
   * @return this instance
   */
  @InterfaceAudience.LimitedPrivate("Impala")
  public S lowerBoundPartitionKeyRaw(byte[] partitionKey) {
    if (Bytes.memcmp(partitionKey, lowerBoundPartitionKey) > 0) {
      this.lowerBoundPartitionKey = partitionKey;
    }
    return (S) this;
  }

  /**
   * Set an encoded (exclusive) end partition key for the scan.
   *
   * @param partitionKey the encoded partition key
   * @return this instance
   */
  @InterfaceAudience.LimitedPrivate("Impala")
  public S exclusiveUpperBoundPartitionKeyRaw(byte[] partitionKey) {
    if (upperBoundPartitionKey.length == 0 || Bytes.memcmp(partitionKey, upperBoundPartitionKey) < 0) {
      this.upperBoundPartitionKey = partitionKey;
    }
    return (S) this;
  }

  public abstract T build();
}