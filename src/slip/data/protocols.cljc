(ns slip.data.protocols)

(defprotocol IRefPath
  (-path [_]))

(defprotocol ICollectRefs
  (-collect-refs [spec refs]))
