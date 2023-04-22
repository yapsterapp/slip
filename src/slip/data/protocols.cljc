(ns slip.data.protocols)

(defprotocol IRefPath
  (-path [_]))

(defprotocol IResolveData
  (-resolve-data [spec sys]))

(defprotocol ICollectRefs
  (-collect-refs [spec refs]))
