(ns hashmap.core_test
  (:require [clojure.test :refer :all]
            [hashmap.core :refer :all])
  (:import [hashmap.core Map]))

(deftype Key [value hash-code]
  Object
  (equals [this other]
    (= value (.value other)))
  (hashCode [this]
    hash-code))

(defn mkey [x]
  (Key. x x))

(deftest make-new-map
  (let [m (new-map)]
    (is (instance? Map (new-map)))
    (is (nil? (mget m 1)))))

(deftest new-map-massoc
  (let [m1 (new-map)
        m2 (massoc m1 1 2)]
    (is (= 2 (mget m2 1)))))