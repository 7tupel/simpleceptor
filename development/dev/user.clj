(ns user
  (:require
   [clj-reload.core :as reload]
   [bling.core :refer [print-bling]]))

;;; Initialize development environment

;; Setup reloading
(reload/init {:no-reload '#{user}})

(require '[lazytest.repl])

(defn start
  "Start everything."
  []
  ::started)


;;; Repl utilities

(defn reload!
  "Reload everything that has changed."
  []
  (reload/reload))


(defn print-welcome
  []
  (println "")
  (print-bling [:bold.blue "Welcome to the Development project for"] " " [:bold.italic.purple "Simpleceptor"])
  (println)
  (print-bling [:gray "The elfs are setting everything up to get you started."] "\n"))

(defn print-ready
  []
  (print-bling [:gray "Everything is setup and ready for you"])
  (print-bling [:gray "Happy Development"] " " [:yellow "🐱"] "\n"))


;;; Start Development

(print-welcome)
(start)
(print-ready)
