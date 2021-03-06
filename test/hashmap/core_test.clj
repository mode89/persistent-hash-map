(ns hashmap.core-test
  (:require [clojure.set :refer (difference)]
            [clojure.test :refer :all]
            [clojure.test.check :as tc]
            [clojure.test.check.clojure-test :refer (defspec)]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [hashmap.core :refer :all])
  (:import [hashmap.core CollisionNode Map]))

(deftype Key [value hash-code]
  Object
  (equals [this other]
    (= value (.value other)))
  (hashCode [this]
    hash-code))

(defn mkey [x]
  (Key. x x))

(defn make-collision-node [n1 n2]
  (assert (= (:key-hash n1) (:key-hash n2)))
  (CollisionNode. (:key-hash n1) [n1 n2]))

(deftest make-new-map
  (let [m (new-map)]
    (is (instance? Map (new-map)))
    (is (nil? (mget m 1)))))

(deftest new-map-massoc
  (let [m1 (new-map)
        m2 (massoc m1 1 2)]
    (is (= 2 (mget m2 1)))))

(deftest massoc-same-key-value
  (let [m1 (new-map)
        m2 (massoc m1 (mkey 1) 2)
        m3 (massoc m2 (mkey 1) 2)]
    (is (identical? m2 m3))))

(deftest massoc-same-key
  (let [m1 (new-map)
        m2 (massoc m1 (mkey 1) 2)
        m3 (massoc m2 (mkey 1) 3)]
    (is (= 3 (mget m3 (mkey 1))))))

(deftest massoc-another
  (let [m1 (new-map)
        m2 (massoc m1 (mkey 1) 2)
        m3 (massoc m2 (mkey 2) 3)]
    (is (not (identical? m2 m3)))
    (is (= 2 (mget m3 (mkey 1))))
    (is (= 3 (mget m3 (mkey 2))))))

(deftest massoc-same-array-index
  (let [m1 (new-map)
        m2 (massoc m1 (mkey 1) 2)
        m3 (massoc m2 (mkey 33) 3)]
    (is (= 2 (mget m3 (mkey 1))))
    (is (= 3 (mget m3 (mkey 33))))))

(deftest collision
  (let [m (-> (new-map)
              (massoc (Key. 1 42) 2)
              (massoc (Key. 3 42) 4))]
    (is (= 2 (mget m (Key. 1 42))))
    (is (= 4 (mget m (Key. 3 42))))))

(deftest overwrite-collision-node
  (let [m (-> (new-map)
              (massoc (Key. 1 42) 2)
              (massoc (Key. 3 42) 4)
              (massoc (Key. 1 42) 5))]
    (is (= 5 (mget m (Key. 1 42))))
    (is (= 4 (mget m (Key. 3 42))))))

(deftest keep-collision-node
  (let [m1 (-> (new-map)
               (massoc (Key. 1 42) 2)
               (massoc (Key. 3 42) 4))
        m2 (massoc m1 (Key. 1 42) 2)]
    (is (identical? m1 m2))))

(deftest add-to-collision-node
  (let [m (-> (new-map)
              (massoc (Key. 1 42) 2)
              (massoc (Key. 3 42) 4)
              (massoc (Key. 5 42) 6))]
    (is (= 2 (mget m (Key. 1 42))))
    (is (= 4 (mget m (Key. 3 42))))
    (is (= 6 (mget m (Key. 5 42))))))

(deftest expand-collision-node
  (let [m (-> (new-map)
              (massoc (Key. 1 42) 2)
              (massoc (Key. 3 42) 4)
              (massoc (Key. 5 5) 6))]
    (is (= 2 (mget m (Key. 1 42))))
    (is (= 4 (mget m (Key. 3 42))))
    (is (= 6 (mget m (Key. 5 5))))))

(deftest dissoc-empty
  (let [m1 (new-map)
        m2 (mdissoc m1 42)]
    (is (identical? m1 m2))))

(deftest dissoc-map-entry-miss
  (let [m1 (new-map)
        m2 (massoc m1 1 42)
        m3 (mdissoc m2 2)]
    (is (identical? m2 m3))))

(deftest dissoc-map-entry-hit
  (let [m1 (new-map)
        m2 (massoc m1 1 42)
        m3 (mdissoc m2 1)]
    (is (identical? m1 m3))))

(deftest dissoc-collision-node-miss-hash
  (let [m1 (-> (new-map)
               (massoc (Key. 1 42) 2)
               (massoc (Key. 3 42) 4))
        m2 (mdissoc m1 (Key. 2 43))]
    (is (identical? m1 m2))))

(deftest dissoc-collision-node-miss-key
  (let [m1 (-> (new-map)
               (massoc (Key. 1 42) 2)
               (massoc (Key. 3 42) 4))
        m2 (mdissoc m1 (Key. 2 42))]
    (is (identical? m1 m2))))

(deftest dissoc-collision-node-hit
  (let [m1 (-> (new-map)
               (massoc (Key. 1 42) 2)
               (massoc (Key. 3 42) 4))
        m2 (mdissoc m1 (Key. 3 42))]
    (is (= 2 (mget m2 (Key. 1 42))))
    (is (nil? (mget m2 (Key. 3 42))))))

(deftest dissoc-collision-node-hit-3
  (let [m1 (-> (new-map)
               (massoc (Key. 1 42) 2)
               (massoc (Key. 3 42) 4)
               (massoc (Key. 5 42) 6))
        m2 (mdissoc m1 (Key. 3 42))]
    (is (= 2 (mget m2 (Key. 1 42))))
    (is (nil? (mget m2 (Key. 3 42))))
    (is (= 6 (mget m2 (Key. 5 42))))))

(deftest dissoc-array-node-miss
  (let [m1 (-> (new-map)
               (massoc (mkey 1) 2)
               (massoc (mkey 3) 4))
        m2 (mdissoc m1 (mkey 2))]
    (is (identical? m1 m2))))

(deftest dissoc-array-node-child-miss
  (let [m1 (-> (new-map)
               (massoc (mkey 1) 2)
               (massoc (mkey 3) 4))
        m2 (mdissoc m1 (mkey 33))]
    (is (identical? m1 m2))))

(deftest dissoc-array-node-remove-child
  (let [m1 (-> (new-map)
               (massoc (mkey 1) 2)
               (massoc (mkey 3) 4))
        m2 (mdissoc m1 (mkey 1))]
    (is (nil? (mget m2 (mkey 1))))
    (is (= 4 (mget m2 (mkey 3))))))

(deftest dissoc-array-node-replace-child
  (let [m1 (-> (new-map)
               (massoc (mkey 1) 11)
               (massoc (mkey 2) 12)
               (massoc (mkey 34) 134))
        m2 (mdissoc m1 (mkey 2))]
    (is (= 11 (mget m2 (mkey 1))))
    (is (nil? (mget m2 (mkey 2))))
    (is (= 134 (mget m2 (mkey 34))))))

(deftest dissoc-array-node-shrink
  (let [m1 (-> (new-map)
               (massoc (mkey 1) 11)
               (massoc (mkey 2) 12))
        m2 (-> m1
               (mdissoc (mkey 1))
               (mdissoc (mkey 2)))]
    (is (identical? m2 (new-map)))))

(deftest entry-difference-test
  (let [e1 (make-entry 1 1)
        e2 (make-entry 2 1)
        e3 (make-entry 1 3)
        e4 (make-entry 1 1)
        e5 (make-entry (Key. 1 42) 1)
        e6 (make-entry (Key. 2 42) 1)]
    (is (nil? (entry-difference e1 e1)))
    (is (nil? (entry-difference nil e1)))
    (is (identical? e1 (entry-difference e1 nil)))
    (is (identical? e1 (entry-difference e1 e2)))
    (is (nil? (entry-difference e1 e4)))
    (is (identical? e5 (entry-difference e5 e6)))))

(deftest node-difference-identical
  (let [e1 (make-entry 1 1)]
    (is (nil? (node-difference 0 e1 e1)))))

(deftest node-difference-nil
  (let [e1 (make-entry 1 1)]
    (is (identical? e1 (node-difference 0 e1 nil)))
    (is (nil? (node-difference 0 nil e1)))))

(deftest node-difference-entry
  (let [k1 (Key. 1 42)
        e1 (make-entry k1 1)
        e2 (make-entry k1 2)
        an (make-array-node e2 0)
        cn (make-collision-node (make-entry (Key. 3 42) 3)
                                (make-entry (Key. 4 42) 4))]
    (is (identical? e1 (node-difference 0 e1 e2)))
    (is (identical? e1 (node-difference 0 e1 an)))
    (is (identical? e1 (node-difference 0 e1 cn)))
    (is (thrown? Exception (node-difference 0 e1 42)))))

(deftest node-difference-array-and-entry
  (let [en (make-entry 1 1)
        an (make-array-node en 0)]
    (is (nil? (node-difference 0 an en)))))

(deftest node-difference-array-and-array
  (let [e1 (make-entry 1 1)
        e2 (make-entry 2 2)
        a1 (make-array-node e1 0)
        a2 (make-array-node e1 0)
        a3 (make-array-node e2 0)]
    (is (nil? (node-difference 0 a1 a2)))
    (let [d (node-difference 0 a1 a3)]
      (is (= 1 (:children-count d)))
      (is (some? (node-get-entry d 0 (:key-hash e1) (:key e1))))
      (is (nil? (node-get-entry d 0 (:key-hash e2) (:key e2)))))))

(def gen-key gen/any-printable-equatable)
(def gen-value gen/string-alpha-numeric)
(defn gen-assoc [ks]
  (gen/tuple (gen/return :assoc) (gen/elements ks) gen-value))
(defn gen-dissoc [ks]
  (gen/tuple (gen/return :dissoc) (gen/elements ks)))
(defn gen-op [ks]
  (gen/one-of [(gen-assoc ks) (gen-dissoc ks)]))
(defn gen-ops [ks]
  (gen/vector (gen-op ks)))
(def gen-keys (gen/not-empty (gen/set gen-key)))
(def gen-ops*
  (gen/let [ks gen-keys]
    (gen/tuple (gen-ops ks) (gen/return ks))))
(defn m-run [ops]
  (reduce (fn [m [op k v]]
            (case op
              :assoc (assoc m k v)
              :dissoc (dissoc m k)
              :else (throw (Exception. "Unknown op"))))
          {} ops))
(defn hm-run [ops]
  (reduce (fn [m [op k v]]
            (case op
              :assoc (massoc m k v)
              :dissoc (mdissoc m k)
              :else (throw (Exception. "Unknown op"))))
          (new-map) ops))

; (defspec property-same-values
;   (prop/for-all [[ops ks] gen-ops*]
;     (let [m (m-run ops)
;           hm (hm-run ops)
;           used-ks (set (keys m))
;           unused-ks (difference ks used-ks)]
;       (and (= (map #(get m %) used-ks)
;               (map #(mget hm %) used-ks))
;            (every? nil? (map #(mget hm %) unused-ks))))))
; 
; (defspec property-same-keys
;   (prop/for-all [[ops] gen-ops*]
;     (let [m (m-run ops)
;           hm (hm-run ops)]
;       (= (set (keys m))
;          (set (mkeys hm))))))
