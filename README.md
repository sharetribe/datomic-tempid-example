# datomic-tempid-example

**Update:** The issue has been [resolved in Datomic 0.9.5561.62](https://groups.google.com/forum/#!topic/datomic/eLVunC7B4Uo/discussion).

This repository illustrates issue with string tempid resolution at least in
Datomic version 0.9.5561.59.

The example project is configured to use `datomic-free`, but `datomic-pro`
behaves in identical way.

## Details

String tempids seem to resolve inconsistently when the transaction is the first
a Datomic transactor executes after starting. The example code in this
repository illustrates this with the `change-email-tx` transaction data. The
behavior of the transaction is different at least in the following situations:

- the transactor and peer have just started and the transactor has not executed
other "problematic" transactions with tempids
- the transactor has seen other transactions that use tempids

Tempids created with `d/tempid` don't seem to suffer from the same issue.

See the [example code](/src/datomic_tempid_example/core.clj) for steps to
reproduce. Make sure you have a Datomic transactor running on `localhost:4334`
or update the `datomic-uri` value in the example.
