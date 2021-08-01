# Changelog

## 0.0.11

* Fix version string reading
* Support release to clojars
* Support use from JS and CLJS

## 0.0.10

* Print out port number on server start
* Remove .nrepl-port on server stop & process exit

## 0.0.9

* Support load-file op
* Work better with cider (fake System/getProperty)
* Write .nrepl-port file on startup, support ephemeral port (leaving port out choosed random port)
* Fix: Make require work after first use

## 0.0.8

* Support (require '["lib" :as lib]) forms
* Loop over all forms in eval op

## 0.0.7

* Report nrepl-cljs-sci version at server start and in describe op
* Used deps.edn for deps (allows local overrides)
* Allow passing existing ctx
* Preserve evaluation context over multiple connections

## 0.0.6

* Support js/require

## 0.0.5

* Fix describe op
* Use pr-str for eval result
* Fix ns binding in eval op
* Include ns in exception response

## 0.0.4

* Allow to send multiple responses. Improve eval (thanks! @borkdude).

## 0.0.3

* Remove .lsp directory from node module

## 0.0.2

* Support clone op. Make eval result into a string

## 0.0.1

* First release, support eval an describe ops
