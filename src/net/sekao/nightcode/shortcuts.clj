(ns net.sekao.nightcode.shortcuts
  (:require [clojure.string :as str]
            [clojure.set :as set]
            [clojure.java.io :as io]
            [net.sekao.nightcode.spec :as spec]
            [clojure.spec :as s :refer [fdef]])
  (:import [javafx.scene Node]
           [javafx.scene Scene]
           [javafx.scene.control Tooltip]
           [javafx.scene.input KeyEvent KeyCode]
           [javafx.stage Stage]
           [javafx.event EventHandler]
           [javafx.beans.value ChangeListener]
           [javafx.application Platform]))

(def id->key-char {; project pane
                   :start "p"
                   :import-project "o"
                   :rename "m"
                   :remove "g"
                   :project-tree "↑ ↓ ↲"
                   ; editor pane
                   :up "u"
                   :save "s"
                   :undo "z"
                   :redo "Z"
                   :instarepl "l"
                   :find "f"
                   :close "w"
                   ; build pane
                   :run "r"
                   :run-with-repl "X"
                   :reload "S"
                   :build "b"
                   :clean "L"
                   :stop "i"
                   ; directory pane
                   :new-file "n"
                   :open-in-browser "F"
                   :edit "M"
                   :cancel "C"})

(def key-char->id (set/map-invert id->key-char))

(fdef keyword->fx-id
  :args (s/cat :k keyword?)
  :ret string?)
(defn keyword->fx-id [k]
  (str "#" (str/replace (name k) #"-" "_")))

(fdef fx-id->keyword
  :args (s/cat :s string?)
  :ret keyword?)
(defn fx-id->keyword [s]
  (-> s (str/replace #"_" "-") keyword))

(fdef add-tooltip!
  :args (s/cat :node spec/node? :text string?))
(defn add-tooltip! [^Node node ^String text]
  (.setTooltip node
    (doto (Tooltip.)
      (.setOpacity 0)
      (.setText text))))

(fdef add-tooltips!
  :args (s/alt
          :nodes (s/cat :nodes (s/coll-of spec/node? []))
          :ids (s/cat :scene spec/scene? :ids (s/coll-of keyword? []))))
(defn add-tooltips!
  ([nodes]
   (doseq [node nodes]
     (when-let [id (.getId node)]
       (when-let [text (get id->key-char (fx-id->keyword id))]
         (when (.isManaged node)
           (add-tooltip! node text))))))
  ([^Scene scene ids]
   (doseq [id ids]
     (let [node (.lookup scene (keyword->fx-id id))
           text (get id->key-char id)]
       (when (and node text (.isManaged node))
         (add-tooltip! node text))))))

(fdef show-tooltip!
  :args (s/alt
          :two-args (s/cat :stage spec/stage? :node spec/node?)
          :three-args (s/cat :stage spec/stage? :node spec/node? :relative-node (s/nilable spec/node?))))
(defn show-tooltip!
  ([^Stage stage ^Node node]
   (show-tooltip! stage node nil))
  ([^Stage stage ^Node node ^Node relative-node]
   (when-let [^Tooltip tooltip (.getTooltip node)]
     (let [node (or relative-node node)
           point (.localToScene node (double 0) (double 0))
           scene (.getScene stage)
           _ (.show tooltip stage (double 0) (double 0))
           half-width (- (/ (.getWidth node) 2)
                         (/ (.getWidth tooltip) 4))
           half-height (- (/ (.getHeight node) 2)
                          (/ (.getHeight tooltip) 4))]
       (doto tooltip
         (.setOpacity 1)
         (.show stage
           (double (+ (.getX point) (.getX scene) (-> scene .getWindow .getX) half-width))
           (double (+ (.getY point) (.getY scene) (-> scene .getWindow .getY) half-height))))))))

(fdef show-tooltips!
  :args (s/cat :stage spec/stage?))
(defn show-tooltips! [^Stage stage]
  (let [scene (.getScene stage)]
    (doseq [id (keys id->key-char)]
      (when-let [node (.lookup scene (keyword->fx-id id))]
        (show-tooltip! stage node)))
    (show-tooltip! stage (.lookup scene "#tabs") (.lookup scene "#content"))))

(fdef hide-tooltip!
  :args (s/cat :node spec/node?))
(defn hide-tooltip! [^Node node]
  (when-let [tooltip (.getTooltip node)]
    (doto tooltip
      (.setOpacity 0)
      (.hide))))

(fdef hide-tooltips!
  :args (s/cat :node (s/or :node spec/node? :stage spec/scene?)))
(defn hide-tooltips! [node]
  (doseq [id (keys id->key-char)]
    (when-let [node (.lookup node (keyword->fx-id id))]
      (hide-tooltip! node)))
  (when (spec/scene? node)
    (hide-tooltip! (.lookup node "#tabs"))))

(fdef init-tabs!
  :args (s/cat :scene spec/scene?))
(defn init-tabs! [^Scene scene]
  (doto (.lookup scene "#tabs")
    (.setManaged false)
    (add-tooltip! "")))

(fdef get-tabs
  :args (s/cat :runtime-state map?)
  :ret (s/coll-of map? []))
(defn get-tabs [runtime-state]
  (->> (-> runtime-state :editor-panes keys)
       (map (fn [path]
              {:path path :file (io/file path)}))
       (filter #(-> % :file .isFile))))

(fdef update-tabs!
  :args (s/cat :scene spec/scene? :pref-state map? :runtime-state map?))
(defn update-tabs! [^Scene scene pref-state runtime-state]
  (let [tabs (.lookup scene "#tabs")
        tooltip (.getTooltip tabs)
        selected-path (:selection pref-state)
        names (map (fn [m]
                     (let [format-str (if (= (:path m) selected-path) "*%s*" "%s")
                           file-name (-> m :file .getName)]
                       (format format-str file-name)))
                (get-tabs runtime-state))
        names (str/join "\n" names)]
    (.setText tooltip (str "PgUp PgDn\n\n" names))))

(fdef run-shortcut!
  :args (s/cat :scene spec/scene? :actions map? :text string? :shift? boolean?))
(defn run-shortcut! [^Scene scene actions ^String text shift?]
  (when-let [id (get key-char->id (if shift? (.toUpperCase text) text))]
    (when-let [action (get actions id)]
      (when-let [node (some-> scene (.lookup (keyword->fx-id id)))]
        (when (and (not (.isDisabled node)) (.isManaged node))
          (Platform/runLater
            (fn []
              (action scene))))))))

(fdef set-shortcut-listeners!
  :args (s/cat :stage spec/stage? :actions map?))
(defn set-shortcut-listeners! [^Stage stage actions]
  (let [^Scene scene (.getScene stage)]
    ; show tooltips on key pressed
    (.addEventHandler scene KeyEvent/KEY_PRESSED
      (reify EventHandler
        (handle [this e]
          (when (#{KeyCode/COMMAND KeyCode/CONTROL} (.getCode e))
            (show-tooltips! stage)))))
    ; hide tooltips and run shortcut on key released
    (.addEventHandler scene KeyEvent/KEY_RELEASED
      (reify EventHandler
        (handle [this e]
          (cond
            (#{KeyCode/COMMAND KeyCode/CONTROL} (.getCode e))
            (hide-tooltips! scene)
            (.isShortcutDown e)
            (if (#{KeyCode/UP KeyCode/DOWN KeyCode/PAGE_UP KeyCode/PAGE_DOWN} (.getCode e))
              ; if any new nodes have appeared, make sure their tooltips are showing
              (Platform/runLater
                (fn []
                  (show-tooltips! stage)))
              ; run the action for the given key
              (run-shortcut! scene actions (.getText e) (.isShiftDown e)))))))
    ; hide tooltips on window focus
    (.addListener (.focusedProperty stage)
      (reify ChangeListener
        (changed [this observable old-value new-value]
          (when new-value
            (hide-tooltips! scene)))))))
