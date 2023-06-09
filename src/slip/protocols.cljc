(ns slip.protocols)

(defprotocol ISlipSystem
  (-spec [_])
  (-reset-spec! [_ spec])
  (-system-map [_])
  (-start! [_ opts resolve-fn reject-fn])
  (-stop! [_ opts resolve-fn reject-fn])
  (-reinit! [_ resolve-fn reject-fn]))
