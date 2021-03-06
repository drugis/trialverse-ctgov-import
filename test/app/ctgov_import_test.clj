(ns app.ctgov-import-test
  (:require [riveted.core :as vtd]
            [org.drugis.addis.rdf.trig :as trig]
            [app.import-shared :as lib])
  (:use clojure.test)
  (:use app.ctgov-import))

(def sustain-2-xml (vtd/navigator (slurp "test/app/sustain-2.xml")))
(def sustain-5-xml (vtd/navigator (slurp "test/app/sustain5ctgov.xml")))
(def age-baseline-xml (first (vtd/search sustain-5-xml "/clinical_study/clinical_results/baseline/measure_list/measure")))
(def age-categorical-xml (second (vtd/search sustain-5-xml "/clinical_study/clinical_results/baseline/measure_list/measure")))
(def sex-baseline-xml (second (vtd/search sustain-2-xml "/clinical_study/clinical_results/baseline/measure_list/measure")))
(def hba1c-change-xml (first (vtd/search sustain-2-xml "/clinical_study/clinical_results/outcome_list/outcome")))
(def sample-size-xml  (vtd/at sustain-5-xml "/clinical_study/clinical_results/baseline/analyzed_list/analyzed"))
(def mm-uri  [:qname :instance "mm-uri"])
(def mm-uris {[:baseline] mm-uri})

(def time-frame-xml-str
  "<clinical_study>
    <clinical_results>
      <outcome_list>
        <outcome>
          <time_frame>19 months</time_frame>
        </outcome>
        <outcome>
          <time_frame>Measured at Month 12</time_frame>
        </outcome>
      </outcome_list>
      <reported_events>
        <time_frame>
          12 months (The course of treatment for each participant in the trial)
        </time_frame>
      </reported_events>
    </clinical_results>
  </clinical_study>")

(def time_frame-xml (vtd/navigator time-frame-xml-str))

(deftest test-find-measurement-moments
  (let [[found-mm-uris found-mm-info] (find-measurement-moments time_frame-xml)]
    (is (= '([:events] [:outcome 1] [:outcome 2] [:baseline])
           (keys found-mm-uris)))
    (is (= '("12 months (The course of treatment for each participant in the trial)"
             "19 months"
             "Measured at Month 12"
             "Baseline")
           (vals found-mm-info)))))

(deftest test-allocation-rdf
  (let [subj [:qname :instance "something"]]
    (is (= [subj [[[:qname :ontology "has_allocation"] 
                  [:qname :ontology "AllocationRandomized"]]]]
           (allocation-rdf subj "Randomized")))
    (is (= [subj [[[:qname :ontology "has_allocation"] 
                   [:qname :ontology "AllocationNonRandomized"]]]]
           (allocation-rdf subj "SomethingElse")))))

(deftest baseline-measurement-properties-continuous-test
  (let [found-properties    (baseline-measurement-properties age-baseline-xml)
        expected-properties {:categories '()
                             :simple     true
                             :param      "Mean"
                             :dispersion "Standard Deviation"
                             :units      "years"}]

    (is (= expected-properties found-properties))))

(deftest baseline-measurement-properties-categorical-test
  (let [found-properties    (baseline-measurement-properties sex-baseline-xml)
        expected-properties {:categories '("Female", "Male")
                             :simple     false
                             :param      "Count of Participants"
                             :dispersion nil
                             :units      "Participants"}]

    (is (= expected-properties found-properties))))

(deftest find-adverse-events-xml-test
  (is (= 121 (count (find-adverse-events-xml sustain-2-xml)))))

(deftest adverse-event-rdf-test
  (let [variable-uris           [[:qname :instance "variable-uri"]]
        mm-uris                 {[:events] [:qname :instance "mm-uri"]}
        expected-rdf-properties '([[:qname :rdf "type"]
                                   [:qname :ontology "AdverseEvent"]]
                                  [[:qname :rdfs "label"]
                                   [:lit "Total, serious adverse events"]]
                                  [[:qname :rdfs "comment"]
                                   [:lit "Total, Total, serious adverse events"]]
                                  [[:qname :ontology "is_measured_at"]
                                   [:qname :instance "mm-uri"]]
                                  [[:qname :ontology "of_variable"]
                                   [:blank ([[:qname :rdf "type"]
                                             [:qname :ontology "Variable"]]
                                            [[:qname :ontology "measurementType"]
                                             [:qname :ontology "dichotomous"]])]]
                                  [[:qname :ontology "has_result_property"]
                                   [:qname :ontology "sample_size"]]
                                  [[:qname :ontology "has_result_property"]
                                   [:qname :ontology "count"]]
                                  [[:qname :ontology "has_result_property"]
                                   [:qname :ontology "event_count"]])]
    (is (= expected-rdf-properties
           (second
            (adverse-event-rdf (first (find-adverse-events-xml sustain-2-xml))
                               0
                               variable-uris
                               mm-uris))))))

(deftest outcome-measurement-type-test
  (is (= "dichotomous" (outcome-measurement-type "Number")))
  (is (= "continuous" (outcome-measurement-type "anything else"))))

(deftest find-outcome-rdf-xml-test
  (is (= 6 (count (find-outcome-rdf-xml sustain-2-xml)))))

(deftest outcome-rdf-test
  (let [outcome-uris            {[:outcome 1] [:qname :instance "outcome-uri"]}
        mm-uris                 {[:outcome 1] [:qname :instance "mm-uri"]}
        expected-rdf-properties '([[:qname :rdf "type"]
                                   [:qname :ontology "Endpoint"]]
                                  [[:qname :rdfs "label"]
                                   [:lit "Change in HbA1c (Glycosylated Haemoglobin) From Baseline"]]
                                  [[:qname :rdfs "comment"]
                                   [:lit "Change in HbA1c from baseline until week 56.Full analysis set (FAS=1225) included all randomised subjects who had received at least one dose of randomised semaglutide or sitagliptin."]]
                                  [[:qname :ontology "is_measured_at"]
                                   [:qname :instance "mm-uri"]]
                                  [[:qname :ontology "has_result_property"]
                                   [:qname :ontology "sample_size"]]
                                  [[:qname :ontology "of_variable"]
                                   [:blank
                                    ([[:qname :rdf "type"]
                                      [:qname :ontology "Variable"]]
                                     [[:qname :ontology "measurementType"]
                                      [:qname :ontology "continuous"]])]]
                                  [[:qname :ontology "has_result_property"]
                                   [:qname :ontology "least_squares_mean"]]
                                  [[:qname :ontology "has_result_property"]
                                   [:qname :ontology "standard_error"]])]
    (is (= expected-rdf-properties
           (second
            (outcome-rdf (first (find-outcome-rdf-xml sustain-2-xml))
                         1
                         outcome-uris
                         mm-uris))))))

(deftest outcome-measurement-properties-test
  (let [found-properties    (outcome-measurement-properties hba1c-change-xml)
        expected-properties {:simple     true
                             :categories ()
                             :param      "Least Squares Mean"
                             :dispersion "Standard Error"
                             :units      "percentage of glycosylated haemoglobin"}]
    (is (= expected-properties found-properties))))

(deftest baseline-var-type-categorical-test
  (let [param                                "Number"
        category-ids                         '("B1" "B2")
        [found-categories found-of-variable] (baseline-var-type {:categories category-ids
                                                                 :param      param})
        category-rdf                         '([[:qname :rdfs "label"]
                                                [:lit "B1"]]
                                               [[:qname :rdf "type"]
                                                [:qname :ontology "Category"]])

        category-rdf-2                       '([[:qname :rdfs "label"]
                                                [:lit "B2"]]
                                               [[:qname :rdf "type"]
                                                [:qname :ontology "Category"]])]
    (is (= category-ids
           (keys (:uris found-categories))))
    (is (= category-rdf
           (second (first (:rdfs found-categories)))))
    (is (= category-rdf-2
           (second (second (:rdfs found-categories)))))
    (is (= [:qname :ontology "of_variable"]
           (first found-of-variable)))
    (is (= :blank
           (first (second found-of-variable))))
    (is (= [[:qname :rdf "type"] [:qname :ontology "Variable"]]
           (first (second (second found-of-variable)))))
    (is (= [[:qname :ontology "measurementType"] [:qname :ontology "categorical"]]
           (second (second (second found-of-variable)))))
    (is (= [:qname :ontology "categoryList"]
           (first (nth (second (second found-of-variable)) 2))))
    (is (= :coll
           (first (second (nth (second (second found-of-variable)) 2)))))
    (is (= :qname
           (first (first (second (second (nth (second (second found-of-variable)) 2)))))))
    (is (= :instance
           (second (first (second (second (nth (second (second found-of-variable)) 2)))))))
    (is (= (count category-ids)
           (count (second (second (nth (second (second found-of-variable)) 2))))))))

(deftest baseline-var-type-non-categorical-test
  (let [param            "continuous"
        category-ids     '()
        expected-values  [nil
                          [[:qname :ontology "of_variable"]
                           [:blank
                            (list [[:qname :rdf "type"]
                                   [:qname :ontology "Variable"]]
                                  [[:qname :ontology "measurementType"]
                                   [:qname :ontology param]])]]]

        found-categories (baseline-var-type {:categories category-ids
                                             :param      param})]
    (println found-categories)
    (is (= found-categories expected-values))))

(deftest baseline-var-type-incorrectly-categorical-test
  (let [param            "nonCount"
        category-ids     '("B1" "B2")
        expected-values  [nil
                          [[:qname :ontology "of_variable"]
                           [:blank
                            '([[:qname :rdf "type"]
                               [:qname :ontology "Variable"]]
                              [[:qname :ontology "measurementType"]
                               [:qname :ontology "incorrectly-categorical"]])]]]

        found-categories (baseline-var-type {:categories category-ids
                                             :param      param})]
    (is (=  expected-values found-categories))))

(deftest group-info-test
  (let [group-xml           (first
                             (vtd/search sustain-2-xml "/clinical_study/clinical_results/participant_flow/group_list/group"))
        found-group-info    (group-info group-xml)
        expected-group-info {:title       "Semaglutide 0.5 mg + Sitagliptin Placebo"
                             :description "Semaglutide 0.5 mg administered subcutaneously (s.c., under the skin) once weekly, in the thigh, abdomen, or upper arm, at any time of day irrespective of meals. Sitagliptin placebo (0 mg) administered orally once daily."}]
    (is (= expected-group-info found-group-info))))

(deftest baseline-var-rdf-continuous-test
  (let [idx                          0
        baseline-uri                 [:qname :instance "baseline-uri"]
        baseline-uris                [baseline-uri]
        [found-categories found-rdf] (baseline-var-rdf age-baseline-xml idx baseline-uris mm-uris)
        expected-rdf                 [[:qname :instance "baseline-uri"]
                                      '([[:qname :rdf "type"] [:qname :ontology "PopulationCharacteristic"]]
                                        [[:qname :rdfs "label"] [:lit "Age"]]
                                        [[:qname :ontology "is_measured_at"] [:qname :instance "mm-uri"]]
                                        [[:qname :ontology "of_variable"]
                                         [:blank
                                          ([[:qname :rdf "type"] [:qname :ontology "Variable"]]
                                           [[:qname :ontology "measurementType"] [:qname :ontology "continuous"]])]]
                                        [[:qname :ontology "has_result_property"]
                                         [:qname :ontology "mean"]]
                                        [[:qname :ontology "has_result_property"]
                                         [:qname :ontology "standard_deviation"]])]]
    (is (= nil found-categories))
    (is (= expected-rdf found-rdf))))

(deftest baseline-var-rdf-categorical-test
  (let [idx                          0
        baseline-uri                 [:qname :instance "baseline-uri"]
        baseline-uris                [baseline-uri]
        [found-categories found-rdf] (baseline-var-rdf age-categorical-xml idx baseline-uris mm-uris)
        _ (println found-rdf)
        expected-categories          '("85 years and over"  "Adults (18-64 years)" "From 65-84 years")
        expected-first-category-rdf  '([[:qname :rdfs "label"]
                                        [:lit "Adults (18-64 years)"]]
                                       [[:qname :rdf "type"]
                                        [:qname :ontology "Category"]])
        expected-properties          '([:qname :rdf "type"]
                                       [:qname :rdfs "label"]
                                       [:qname :ontology "is_measured_at"]
                                       [:qname :ontology "of_variable"]
                                       [:qname :ontology "has_result_property"]
                                       [:qname :ontology "has_result_property"])]
    (is (lib/same-ignoring-order? expected-categories (keys (:uris found-categories))))
    (is (= (count (:rdfs found-categories)) (count (:uris found-categories))))
    (is (= expected-first-category-rdf (second (first (:rdfs found-categories)))))
    (is (= baseline-uri (first found-rdf)))
    (is (= expected-properties (map first (second found-rdf))))))

(deftest baseline-measurement-data-rdf-continuous-test
  (let [subj            [:qname :instance "measurement-uuid"]
        group-id        "B1"
        category-uris   {}
        expected-data   [[:qname :instance "measurement-uuid"]
                         '([[:qname :ontology "sample_size"] [:lit 132]]
                           [[:qname :ontology "mean"] [:lit 59.1]]
                           [[:qname :ontology "standard_deviation"] [:lit 10.3]])]
        found-data      (baseline-measurement-data-rdf subj age-baseline-xml
                                                       sample-size-xml group-id
                                                       category-uris)]
    (is (= expected-data found-data))))

(deftest baseline-measurement-data-rdf-categorical-test
  (let [subj               [:qname :instance "measurement-uuid"]
        group-id           "B1"
        category-uris      {"Adults (18-64 years)" [:qname :instance "adults"]
                            "From 65-84 years"     [:qname :instance "pensioners"]
                            "85 years and over"    [:qname :instance "octogenarians"]}
        expected-data      [[:qname :instance "measurement-uuid"]
                            '([[:qname :ontology "category_count"]
                               [:blank
                                ([[:qname :ontology "category"]
                                  [:qname :instance "adults"]]
                                 [[:qname :ontology "count"] [:lit 93]])]]
                              [[:qname :ontology "category_count"]
                               [:blank
                                ([[:qname :ontology "category"]
                                  [:qname :instance "pensioners"]]
                                 [[:qname :ontology "count"] [:lit 39]])]]
                              [[:qname :ontology "category_count"]
                               [:blank
                                ([[:qname :ontology "category"]
                                  [:qname :instance "octogenarians"]]
                                 [[:qname :ontology "count"] [:lit 0]])]])]

        found-data         (baseline-measurement-data-rdf subj age-categorical-xml
                                                          sample-size-xml group-id
                                                          category-uris)]
    (is (= expected-data found-data))))

(def test-xml (vtd/navigator (slurp "test/app/testxml3.xml")))

(deftest testxml-3
  (let [imported-rdf (import-xml test-xml)]
    (is (= 374542 (count imported-rdf)))))

(deftest testxml-sustain2
  (let [imported-rdf (import-xml (vtd/navigator (slurp "test/app/sustain-2.xml")))]
    ; (spit "out-sustain2.rdf" imported-rdf)
    (is (= 264612 (count imported-rdf)))))

(deftest testxml-sustain5
  (let [imported-rdf (import-xml (vtd/navigator (slurp "test/app/sustain5ctgov.xml")))]
    ; (spit "out-sustain5.rdf" imported-rdf)
    (is (= 121916 (count imported-rdf)))))
