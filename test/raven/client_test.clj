(ns raven.client-test
  (:require [clojure.test :refer :all]
            [raven.client :refer :all]))

(def dsn-fixture
  "https://098f6bcd4621d373cade4e832627b4f6:ad0234829205b9033196ba818f7a872b@sentry.example.com/42")

(def expected-parsed-dsn
  {:key "098f6bcd4621d373cade4e832627b4f6"
   :secret "ad0234829205b9033196ba818f7a872b"
   :uri "https://sentry.example.com"
   :pid 42})

(def expected-sig
  "75e297d21055bbd1b51229f266d71701e1b70e68")

(def frozen-ts
  1525265277.63)

(def frozen-uuid
  "a059419cd1bd46a685b95080f260aed4")

(def frozen-servername
  "Muninn")

(def expected-message
  "a test message")

(def expected-header
  (str "Sentry sentry_version=2.0, sentry_signature=" expected-sig ", sentry_timestamp=" frozen-ts ", sentry_client=spootnik-raven/0.1.4, sentry_key=" (:key expected-parsed-dsn)))

(def expected-user-id
  "Huginn")

(def expected-payload
  {:level "error"
   :server_name frozen-servername
   :culprit "<none>"
   :timestamp frozen-ts
   :platform "java"
   :event_id frozen-uuid
   :project 42
   :message expected-message})

(def expected-breadcrumb
  {:type "default"
   :timestamp frozen-ts
   :level "info"
   :message "message"
   :category "category"})

(def simple-http-info
  {:url "http://example.com"
   :method "POST"})

(defn reset-storage
  "A fixture to reset the per-thread atom between tests."
  [f]
  (do
    (f)
    (clear-context)))

(use-fixtures :each reset-storage)

(deftest raven-client-tests
  (testing "parsing DSN"
    (is (= (parse-dsn dsn-fixture) expected-parsed-dsn)))

  (testing "signing"
    (is (= (sign "payload" frozen-ts (:key expected-parsed-dsn) (:secret expected-parsed-dsn)) expected-sig)))

  (testing "the auth header is what we expect"
    (is (= (auth-header frozen-ts (:key expected-parsed-dsn) expected-sig) expected-header)))

  (testing "the payload is constructed from a map"
    (is (= expected-payload (payload {} {:message expected-message} frozen-ts 42 frozen-uuid frozen-servername))))

  (testing "the payload is constructed from a string"
    (is (= expected-payload (payload {} expected-message frozen-ts 42 frozen-uuid frozen-servername)))))

(deftest gather-breadcrumbs
  (testing "we can gather breadcrumbs"
    (do
      (add-breadcrumb! (make-breadcrumb! (:message expected-breadcrumb) (:category expected-breadcrumb) (:level expected-breadcrumb) frozen-ts))
      (is (= [expected-breadcrumb] (:breadcrumbs @@thread-storage))))))

(deftest add-breadcrumbs
  (testing "breadcrumbs are added to the payload"
    (do
      (add-breadcrumb! (make-breadcrumb! (:message expected-breadcrumb) (:category expected-breadcrumb) (:level expected-breadcrumb) frozen-ts))
      (is (= expected-breadcrumb (first (:values (:breadcrumbs (payload @@thread-storage expected-message frozen-ts 42 frozen-uuid frozen-servername)))))))))

(deftest multi-breadcrumbs
  (testing "adding several breadcrumbs to the payload"
    (do
      (add-breadcrumb! (make-breadcrumb! (:message expected-breadcrumb) (:category expected-breadcrumb) (:level expected-breadcrumb) frozen-ts))
      (add-breadcrumb! (make-breadcrumb! (:message expected-breadcrumb) (:category expected-breadcrumb) (:level expected-breadcrumb) frozen-ts))
      (is (= 2 (count (:values (:breadcrumbs (payload @@thread-storage expected-message frozen-ts 42 frozen-uuid frozen-servername)))))))))

(deftest multi-thread
  (testing "breadcrumbs are thread local"
    (do
      (add-breadcrumb! (make-breadcrumb! (:message expected-breadcrumb) (:category expected-breadcrumb) (:level expected-breadcrumb) frozen-ts))
      (add-breadcrumb! (make-breadcrumb! (:message expected-breadcrumb) (:category expected-breadcrumb) (:level expected-breadcrumb) frozen-ts))
      (is (nil? @(future (:breadcrumbs (payload @@thread-storage expected-message frozen-ts 42 frozen-uuid frozen-servername))))))))

(deftest manual-context
  (testing "breadcrumbs are sent using a manual context."
    (let [context {:breadcrumbs [(make-breadcrumb! (:message expected-breadcrumb) (:category expected-breadcrumb) (:level expected-breadcrumb) frozen-ts)]}]
      (is (= expected-breadcrumb (first (:values (:breadcrumbs (payload context expected-message frozen-ts 42 frozen-uuid frozen-servername)))))))))

(deftest add-user
  (testing "user is added to the payload"
    (do
      (add-user! (make-user expected-user-id))
      (is (= expected-user-id (:id (:user (payload @@thread-storage expected-message frozen-ts 42 frozen-uuid frozen-servername))))))))

(deftest manual-user
  (testing "user is sent using a manual context"
    (let [context {:user (make-user expected-user-id)}]
      (is (= expected-user-id (:id (:user (payload context expected-message frozen-ts 42 frozen-uuid frozen-servername))))))))

(deftest add-request
  (testing "http information is added to the payload"
    (do
      (add-http-info! (make-http-info (:url simple-http-info) (:method simple-http-info)))
      (is (= simple-http-info (:request (payload @@thread-storage expected-message frozen-ts 42 frozen-uuid frozen-servername)))))))

(deftest manual-request
  (testing "http information is sent using a manual context"

    (let [context {:request (make-http-info (:url simple-http-info) (:method simple-http-info))}]
      (is (= simple-http-info (:request (payload context expected-message frozen-ts 42 frozen-uuid frozen-servername)))))))
