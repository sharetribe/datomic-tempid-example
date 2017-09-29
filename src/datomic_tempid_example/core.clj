(ns datomic-tempid-example.core
  (:gen-class)
  (:require [datomic.api :as d]))

;; String tempids seem to resolve inconsistently when the transaction is the
;; first a Datomic transactor executes after starting. The example code below
;; illustrates this with the `change-email-tx` transaction data. The behavior
;; of the transaction is different at least in the following situations:
;;
;; - the transactor and peer have just started and the transactor has not
;; executed other "problematic" transactions with tempids
;; - the transactor has seen other transactions that use tempids
;;
;; Tempids created with `d/tempid` don't seem to suffer from the same issue.

;; See comment section in the bottom for steps to reproduce issue.

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!"))

(def datomic-uri "datomic:free://localhost:4334/datomic-tempid-test")

(def ^:const user-id #uuid "59cdf5a6-6312-4b07-948a-f43ecb5ad13e")

(def schema-tx
  [{:db/ident :user/id
    :db/valueType :db.type/uuid
    :db/cardinality :db.cardinality/one
    :db/unique :db.unique/identity
    :db/doc "User's public id."}
   {:db/ident :user/email
    :db/valueType :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/doc "User's email."}

   {:db/ident :email/address
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "Email address."}
   ])

;; This transaction data triggers the issue
(def create-user-tx
  [{:db/id "new-email"
    :email/address "original@example.com"}
   {:user/id user-id
    :user/email "new-email"}])

(def delete-user-tx
  [[:db/retractEntity [:user/id user-id]]])

;; This transaction data triggers the issue
(defn- change-email-tx
  [email]
  [{:db/id "new-email"
    :email/address email}
   {:user/id user-id
    :user/email "new-email"}])

;; This transaction data DOES NOT trigger the issue
(defn- change-email-safe-non-string-tmpids-tx
  [email]
  (let [email-tid (d/tempid :db.part/user)]
    [{:db/id email-tid
      :email/address email}
     {:user/id user-id
      :user/email email-tid}]))

;; This transaction data DOES NOT trigger the issue either
(defn- change-email-safe-tx
  [email]
  [{:db/id "new-email"
    :email/address email}
   {:db/id [:user/id user-id]
    :user/email "new-email"}])

(defn- get-user-attrs [conn]
  (-> conn
      d/db
      (d/entity [:user/id user-id])
      d/touch
      keys))

(comment
  ;; 1. Start local transactor

  ;; 2. Create database
  (d/create-database datomic-uri)

  ;; 3. Connect to database
  (def conn (d/connect datomic-uri))

  ;; 4. Create schema
  @(d/transact conn schema-tx)

  ;; 5. Create a user
  @(d/transact conn create-user-tx)

  ;; 6. Change user's email
  @(d/transact conn (change-email-tx "test1@example.com"))

  ;; Verify that user has only :user/id and :user/email attributes
  (get-user-attrs conn)

  ;; 7. Stop repl, stop local transactor

  ;; 8. Start local transactor, start repl

  ;; 9. Connect to database
  (def conn (d/connect datomic-uri))

  ;; 10. Change email
  @(d/transact conn (change-email-tx "test2@example.com"))

  ;; UNEXPECTED RESULT: the user entity has also :email/address attribute
  ;; i.e. the "new-email" tempid was resolved to the user entity ID
  (get-user-attrs conn)

  ;; Further, at this point if we retract and re-create the user (even
  ;; repeatedly), the same issue manifests:
  @(d/transact conn delete-user-tx)

  @(d/transact conn create-user-tx)

  (get-user-attrs conn)

  )
