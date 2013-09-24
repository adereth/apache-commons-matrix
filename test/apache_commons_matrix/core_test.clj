(ns apache-commons-matrix.core-test
  (:use clojure.test
        apache-commons-matrix.core)
  (:require [clojure.core.matrix.compliance-tester :as ct])
  (:import [org.apache.commons.math3.linear Array2DRowRealMatrix]))

(deftest compliance-test
  (ct/compliance-test (Array2DRowRealMatrix. 1 1)))
