(ns schema-plus.core-test
  (:require [clojure.set :as sets]
            [clojure.test :refer [deftest is testing]]
            [clojure.test.check.generators :as cg]
            [schema.core :as s]
            [schema-plus.core :refer [defschema+ generate process-opts set-generator]])
  (:import [java.util Date UUID]))

(deftest options-processing-test
  (testing "Bad args"
    (is (thrown? RuntimeException (process-opts [:generator])))
    (is (thrown? RuntimeException (process-opts [:badkey 123])))
    (is (thrown? RuntimeException (process-opts [:docs "Whatever" :badkey 123])))))

(deftest metadata-test
  (testing "Doc metadata is attached correctly"
    (defschema+ WithDocs
      {:a s/Int}
      :docs "The Docs")
    (is (= "The Docs" (-> WithDocs meta :json-schema :description)))
    (defschema+ WithoutDocs
      {:a s/Int})
    (is (= "" (-> WithoutDocs meta :json-schema :description))))

  (testing "Example metadata is attached correctly"
    (defschema+ WithExample
      {:a s/Int}
      :example {:a 123})
    (is (= {:a 123} (-> WithExample meta :json-schema :example)))
    (defschema+ WithoutExample
      {:a s/Int})
    (is (= [:a] (-> WithExample meta :json-schema :example keys)))))

(deftest default-generator-test
  (testing "Map With Simple Types"
    (defschema+ MyMap
      {:int s/Int
       :str s/Str
       :bool s/Bool
       :num s/Num})
    (let [gen (generate MyMap)]
      (is (= #{:int :str :bool :num} (-> gen keys set)))
      (is (integer? (:int gen)))
      (is (string? (:str gen)))
      (is (contains? #{true false} (:bool gen)))
      (is (number? (:num gen)))))

  (testing "Map With Java Class Types"
    (defschema+ MyJavaMap
      {:uuid UUID
       :date Date})
    (let [gen (generate MyJavaMap)]
      (is (instance? UUID (:uuid gen)))
      (is (instance? Date (:date gen)))))

  (testing "Referential Map Types"
    (defschema+ MyMapA
      {:a s/Int
       :b s/Str})
    (defschema+ MyMapB
      {:x s/Int
       :y MyMapA})
    (let [gen (generate MyMapB)]
      (is (= #{:x :y} (-> gen keys set)))
      (is (integer? (:x gen)))
      (is (map? (:y gen)))
      (is (integer? (-> gen :y :a)))
      (is (string? (-> gen :y :b)))))

  (testing "Simple Vector Types"
    (defschema+ MyInts [s/Int])
    (is (every? integer? (generate MyInts)))
    (defschema+ MyStrs [s/Str])
    (is (every? string? (generate MyStrs))))

  (testing "Referential Vector Types"
    (defschema+ MyMapC
      {:a s/Int
       :b s/Str})
    (defschema+ MyVector [MyMapC])
    (let [gen (generate MyVector)]
      (is (every? map? gen))
      (is (every? #(-> % :a integer?) gen))
      (is (every? #(-> % :b string?) gen))))

  (testing "Map With Optional Keys"
    (defschema+ MyOptionalMap
      {:a s/Int
       (s/optional-key :b) s/Str})
    (s/validate MyOptionalMap {:a 1})
    (s/validate MyOptionalMap {:a 1 :b "foo"})
    (let [gen (generate MyOptionalMap)]
      (is (empty? (-> gen keys set (sets/difference #{:a :b}))))
      (is (integer? (:a gen)))
      (is (or (nil? (:b gen))
              (string? (:b gen))))))

  (testing "Map With Maybe Value"
    (defschema+ MyMaybeMap
      {:a s/Int
       :b (s/maybe s/Str)})
    (s/validate MyMaybeMap {:a 1 :b nil})
    (s/validate MyMaybeMap {:a 1 :b "foo"})
    (let [gen (generate MyMaybeMap)]
      (is (= #{:a :b} (-> gen keys set)))
      (is (integer? (:a gen)))
      (is (or (nil? (:b gen))
              (string? (:b gen))))))

  (testing "Map With Constrained Value"
    (defschema+ MyConstrainedValueMap
      {:a s/Int
       :b (s/constrained s/Str #(not (nil? %)))})
    (s/validate MyConstrainedValueMap {:a 1 :b "foo"})
    (let [gen (generate MyConstrainedValueMap)]
      (is (= #{:a :b} (-> gen keys set)))
      (is (integer? (:a gen)))
      (is (string? (:b gen)))))

  (testing "Fully Constrained Map"
    (defschema+ MyConstrainedMap
      (s/constrained
        {:a s/Int
         :b s/Str}
        (constantly true)))
    (s/validate MyConstrainedMap {:a 1 :b "foo"})
    (let [gen (generate MyConstrainedMap)]
      (is (= #{:a :b} (-> gen keys set)))
      (is (integer? (:a gen)))
      (is (string? (:b gen)))))

  (testing "Constrained Simple Value"
    (defschema+ MyConstrainedInt
      (s/constrained s/Int integer?))
    (s/validate MyConstrainedInt 1)
    (is (integer? (generate MyConstrainedInt))))

(testing "Enums"
  (defschema+ MyEnum
    (s/enum :foo :bar :baz))
  (s/validate MyEnum :foo)
  (is (contains? #{:foo :bar :baz} (generate MyEnum)))))

(deftest test-generator-customization
  (testing "Full Generator Replacement"
    (defschema+ MyCustomGenMap
      {:a s/Int
       :b s/Str}
      :generator
      (cg/return {:a 123 :b "foo"}))
    (is (= {:a 123 :b "foo"} (generate MyCustomGenMap)))

    (defschema+ Zipcode
      (s/constrained s/Str #(re-matches #"^\d{5}$" %))
      :generator
      (cg/return "90210"))
    (is (= "90210" (generate Zipcode))))

  (testing "Generator FMap Function"
    (defschema+ MyCustomFMap
      {:a s/Int
       :b s/Str}
      :generator
      #(assoc % :a 123))
    (let [gen (generate MyCustomFMap)]
      (is (= 123 (:a gen)))
      (is (string? (:b gen)))))

  (testing "Nested FMap Customization"
    (defschema+ MyCustomFMapInner
      {:a s/Int
       :b s/Str}
      :generator
      #(assoc % :a 123))
    (defschema+ MyCustomFMapOuter
      {:a MyCustomFMapInner
       :b s/Str}
      :generator
      #(assoc % :b (-> % :a :b)))
    (let [gen (generate MyCustomFMapOuter)]
      (is (= 123 (-> gen :a :a)))
      (is (string? (:b gen)))
      (is (= (:b gen) (-> gen :a :b)))))

  (testing "Generating fields with direct Java Classes"
    (set-generator Object (cg/return "abc"))
    (defschema+ MyWeirdMap
      {:a Object})
    (is (= {:a "abc"} (generate MyWeirdMap)))))

; These declarations aren't necessary, but make Joker happy. These would
; normally be avoided by properly importing from another namespace where
; the schema is declared.
(declare +MyPerson)
(declare +MyPerson-with-name)
(declare +MyPerson-with-age)
(declare +MyPerson-build)
(declare +MyPerson->)
(defschema+ MyPerson
  {:name s/Str
    :age s/Int
    (s/optional-key :occupation) s/Str})

(deftest test-builder-functions

  (testing "Build with all mandatory fields"
    (is (= {:age 42 :name "Bob"}
           (-> (+MyPerson)
               (+MyPerson-with-name "Bob")
               (+MyPerson-with-age 42)
               (+MyPerson-build)))))

  (testing "Build with +MyPerson->"
    (is (= {:age 42 :name "Bob"}
           (+MyPerson-> (+MyPerson)
                        (+MyPerson-with-name "Bob")
                        (+MyPerson-with-age 42)))))

  (testing "Build with +MyPerson-> starting with generated value"
    (let [final (+MyPerson-> (generate MyPerson)
                             (+MyPerson-with-name "Bob")
                             (+MyPerson-with-age 2))]
      (is (= "Bob" (:name final)))
      (is (integer? (:age final)))
      (is (empty? (-> final keys set (sets/difference #{:name :age :occupation}))))))

  (testing "Build with all fields"
    (is (= {:age 42 :name "Bob" :occupation "Builder"}
           (-> (+MyPerson)
               (+MyPerson-with-name "Bob")
               (+MyPerson-with-age 42)
               (+MyPerson-with-occupation "Builder")
               (+MyPerson-build)))))

  (testing "Missing a mandatory field results in error"
    (is
      (thrown? Throwable
               (-> (+MyPerson)
                   (+MyPerson-with-age 42)
                   (+MyPerson-build)))))

  (testing "Failing to finalize means schema validation will fail"
    (is
      (thrown? Throwable
               (s/validate
                 MyPerson
                 (-> (+MyPerson)
                     (+MyPerson-with-age 42)
                     (+MyPerson-with-name "Bob"))))))

  (testing "An initial map can be passed in"
    (is (= {:name "Bob" :age 42}
           (-> (+MyPerson {:name "Bob" :age 42})
               (+MyPerson-build))))))
