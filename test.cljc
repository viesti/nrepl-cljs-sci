;; Try loading this in Emacs via cider
(ns todo
  (:require ["child_process" :as cp]))

(cp/spawnSync "ls")
