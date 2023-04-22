(ns slip.multimethods)

(defmulti start
  (fn [factory-key _data]
    factory-key))

(defmulti stop
  (fn [factory-key _data _obj]
    factory-key))

(defmethod stop :default
  [_factory-key _data _obj])
