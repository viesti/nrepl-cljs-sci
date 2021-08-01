# nrepl-cljs-sci

[nREPL](https://nrepl.org/nrepl/0.8/index.html) server for NodeJS using [SCI](https://github.com/borkdude/sci).

## Usage

nrepl-cljs-sci can be used both in plain JS projects and in CLJS projects. See [example/js](./example/js) for JS project example and [example/cljs](./example/cljs).

The `start_server` (`start-server` in CLJS) function takes an argument, that can be a JS object or a Clojure map. You can pass a reference to application state under the `app` key, which is the available at the nrepl repl under `app/app` symbol.

For example, use from JS project:

```
$ cd example/js
$ node index.js
Example app listening at http://localhost:3000
2021-08-01T18:48:35.553Z INFO [nrepl-cljs-sci.core:206] - nRepl server started on port 59600. nrepl-cljs-sci version 0.0.10
```

Then, in another shell:

```
$ curl http://localhost:3000
Hello World!
```

Connect to the nRepl server and run the following:

```
user> app/app
#js {:app #object[app], :root #js {:response "Hello World!"}}
user> (set! (.-response (.-root app/app)) "Hello SCI!")
nil
```

Now, check the result via curl:

```
$ curl http://localhost:3000
Hello SCI!
```

## Development instructions

1. Start shadow-cljs server (for faster compile invocations)

```shell
bb shadow-server
```

2. Compile nrepl-cljs-sci to JavaScript

```
bb shadow-compile
```

3. Run nrepl-cljs-sci

```
bb node-server
```

4. Run nrepl client

```
bb nrepl-client
nREPL server started on port 54784 on host localhost - nrepl://localhost:54784
[Rebel readline] Type :repl/help for online help info
```

5. Send nrepl requests to the server

```
user=> (require '[nrepl.core :as nrepl])
nil
user=> (with-open [conn (nrepl/connect :port 7788)]
  #_=>   (let [response (nrepl/message (nrepl/client conn 1000) {:op "describe"})]
  #_=>     (println response)))
({:aux {}, :id af22bc8e-164a-4d22-b67d-6c676f1262a0, :ops {:describe {}, :eval {}}, :status [done], :versions {:nghttp2 1.41.0, :napi 7, :modules 83, :brotli 1.0.9, :zlib 1.2.11, :tz 2020a, :v8 8.4.371.19-node.18, :node 14.16.0, :openssl 1.1.1j, :icu 67.1, :cldr 37.0, :ares 1.16.1, :llhttp 2.1.3, :unicode 13.0, :uv 1.40.0}})
nil
user=> (with-open [conn (nrepl/connect :port 7788)]
  #_=>   (let [response (nrepl/message (nrepl/client conn 1000) {:op "eval" :code "(+ 1 1)"})]
  #_=>     (println response)))
({:id d4c78721-61de-4b20-a428-2fa941d49eb1, :ns user, :status [done], :value 2})
;; JS interop works too
user=> (with-open [conn (nrepl/connect :port 7788)]
  #_=>   (let [response (nrepl/message (nrepl/client conn 1000) {:op "eval" :code "js/process.versions"})]
  #_=>     (println response)))
({:id 0360a3a9-7899-417b-a195-ff999e0d2486, :ns user, :status [done], :value {:nghttp2 1.41.0, :napi 7, :modules 83, :brotli 1.0.9, :zlib 1.2.11, :tz 2020a, :v8 8.4.371.19-node.18, :node 14.16.0, :openssl 1.1.1j, :icu 67.1, :cldr 37.0, :ares 1.16.1, :llhttp 2.1.3, :unicode 13.0, :uv 1.40.0}})
```

## Changelog

See [CHANGELOG.md](CHANGELOG.md)
