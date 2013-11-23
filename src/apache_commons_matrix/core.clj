(ns apache-commons-matrix.core
  (:require [clojure.core.matrix.protocols :as mp]
            [clojure.core.matrix.implementations :as imp])  
  (:import [org.apache.commons.math3.linear
            Array2DRowRealMatrix RealMatrix
            ArrayRealVector RealVector
            RealMatrixChangingVisitor]))

(extend-protocol mp/PImplementation
  RealMatrix
    (implementation-key [m] :apache-commons)
    (new-vector [m length] (ArrayRealVector. length))
    (new-matrix [m rows columns] (Array2DRowRealMatrix. rows columns))
    (new-matrix-nd [m dims]
      (case (count dims)
            0 0.0
            1 (ArrayRealVector. (first dims))
            2 (Array2DRowRealMatrix. (first dims) (second dims))
            (throw (ex-info "Apache Commons Math matrices only supports up to 2 dimensions")
                   {:requested-shape dims})))
    (construct-matrix [m data]
      (case (mp/dimensionality data)
            0 data
            1 (ArrayRealVector. ^doubles (mp/to-double-array data))
            2 (Array2DRowRealMatrix. (into-array (map mp/to-double-array (mp/get-major-slice-seq data))))))
    (supports-dimensionality? [m dims] (<= 1 dims 2)))

(extend-protocol mp/PImplementation
  RealVector
    (implementation-key [m] :apache-commons)
    (new-vector [m length] (ArrayRealVector. length))
    (new-matrix [m rows columns] (Array2DRowRealMatrix. rows columns))
    (new-matrix-nd [m dims]
      (case (count dims)
            0 0.0
            1 (ArrayRealVector. (first dims))
            2 (Array2DRowRealMatrix. (first dims) (second dims))
            (throw (ex-info "Apache Commons Math matrices only supports up to 2 dimensions")
                   {:requested-shape dims})))
    (construct-matrix [m data]
      (case (mp/dimensionality data)
            0 data
            1 (ArrayRealVector. ^doubles (mp/to-double-array data))
            2 (Array2DRowRealMatrix. (into-array (map mp/to-double-array (mp/get-major-slice-seq data))))))
    (supports-dimensionality? [m dims] (<= 1 dims 2)))

(extend-protocol mp/PDimensionInfo
  RealMatrix
  (dimensionality [m] 2)
  (get-shape [m] (list (.getRowDimension m) (.getColumnDimension m)))
  (is-scalar? [m] false)
  (is-vector? [m] false)
  (dimension-count [m dimension-number]
    (case dimension-number
          0 (.getRowDimension m)
          1 (.getColumnDimension m)
          (throw (ex-info "RealMatrix only has 2 dimensions"
                          {:requested-dimension dimension-number}))))

  RealVector
  (dimensionality [v] 1)
  (get-shape [v] [(.getDimension v)])
  (is-scalar? [v] false)
  (is-vector? [v] true)
  (dimension-count [v dimension-number]
    (if (zero? dimension-number)
      (.getDimension v)
      (throw (ex-info "RealVector only has 1 dimension"
                      {:requested-dimension dimension-number})))))

(extend-protocol mp/PIndexedAccess
  RealMatrix
  (get-1d [m row] (.getRowVector m row))
  (get-2d [m row column] (.getEntry m row column))
  (get-nd [m indexes]
    (case (count indexes)
          1 (mp/get-1d m (first indexes))
          2 (mp/get-2d m (first indexes) (second indexes))
          (throw (ex-info "RealMatrix only has 2 dimensions"
                    {:requested-index indexes
                     :index-count (count indexes)}))))
  
  RealVector
  (get-1d [v index] (.getEntry v index))
  (get-2d [v row column]
    (throw (ex-info "RealVector only has 1 dimension"
                    {:index-count 2})))
  (get-nd [v indexes]
    (if (= (count indexes) 1)
      (mp/get-1d v (first indexes))
      (throw (ex-info "RealVector only has 1 dimension"
                      {:requested-index indexes
                       :index-count (count indexes)})))))

(extend-protocol mp/PIndexedSetting
  RealMatrix
  (set-1d [m row e] (mp/set-1d! (.copy m) row e))
  (set-2d [m row column e] (mp/set-2d! (.copy m) row column e))
  (set-nd [m indexes e] (mp/set-nd! (.copy m) indexes e))
  (is-mutable? [m] true)
  
  RealVector
  (set-1d [v index e] (mp/set-1d! (.copy v) index e))
  (set-2d [v row column e] (mp/set-2d! (.copy v) row column e))
  (set-nd [v indexes e] (mp/set-nd! (.copy v) indexes e))
  (is-mutable? [m] true))

(extend-protocol mp/PIndexedSettingMutable
  RealMatrix
  (set-1d! [m row e]
    (if (mp/is-vector? e)
      (doto m (.setRow row e))
      (throw (ex-info "Unable to set row" {}))))
  (set-2d! [m row column e] (doto m (.setEntry row column e)))
  (set-nd! [m indexes e])
  
  RealVector
  (set-1d! [v index e] (doto v (.setEntry index e)))
  (set-2d! [v row column e] (mp/set-nd! v [row column] e))
  (set-nd! [v indexes e]
    (if (= (count indexes) 1)
      (mp/set-1d! v (first indexes) e)
      (throw (ex-info "RealVector only has 1 dimension"
                      {:requested-index indexes
                       :index-count (count indexes)})))))

(extend-protocol mp/PMatrixCloning
  RealMatrix
  (clone [m] (.copy m))

  RealVector
  (clone [v] (.copy v)))

(extend-protocol mp/PTypeInfo
  RealMatrix
  (element-type [m] Double/TYPE)

  RealVector
  (element-type [v] Double/TYPE))

(extend-protocol mp/PMutableMatrixConstruction
  RealMatrix
  (mutable-matrix [m] (.copy m))

  RealVector
  (mutable-matrix [v] (.copy v)))

(extend-protocol mp/PMatrixScaling
  RealMatrix
  (scale [m a] (.scalarMultiply m a))
  (pre-scale [m a] (.scalarMultiply m a))

  RealVector
  (scale [v a] (.mapMultiply v a))
  (pre-scale [v a] (.mapMultiply v a)))

(extend-protocol mp/PMatrixMutableScaling
  RealMatrix
  (scale! [m a] (doto m
                  (.walkInOptimizedOrder (reify RealMatrixChangingVisitor
                                           (end [_] 0.0)
                                           (start [_ _ _ _ _ _ _])
                                           (visit [_ row column value] (* a value))))))
  (pre-scale! [m a] (mp/scale! m a)))

(extend-protocol mp/PNegation
  RealMatrix
  (negate [m] (mp/scale m -1))

  RealVector
  (negate [v] (mp/scale v -1)))

(extend-protocol mp/PTranspose
  RealMatrix
  (transpose [m] (.transpose m)))

(extend-protocol mp/PVectorOps
  RealVector
  (vector-dot [a b] (.dotProduct a (mp/coerce-param a b)))
  (length [a] (.getNorm a))
  (length-squared [a] (let [l (.getNorm a)] (* l l)))
  (normalise [a] (.unitVector a)))

(extend-protocol mp/PMutableVectorOps
  RealVector
  (normalise! [a] (doto a (.unitize))))

(extend-protocol mp/PVectorDistance
  RealVector
  (distance [a b] (.getDistance a (mp/coerce-param a b))))

(imp/register-implementation (Array2DRowRealMatrix. 1 1))
