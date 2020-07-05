(ns status-im.ui.screens.chat.components.accessory
  (:require [quo.animated :as animated]
            [reagent.core :as reagent]
            [cljs-bean.core :as bean]
            [quo.hooks :refer [use-keyboard-dimension]]
            [quo.react :as react]
            [quo.react-native :as rn]
            [quo.components.safe-area :refer [use-safe-area]]))

(def tabbar-height 36)

(defn create-pan-responder [y]
  (js->clj (.-panHandlers
            ^js (.create
                 ^js rn/pan-responder
                 #js {:onPanResponderMove (fn [_ ^js state]
                                            (animated/set-value y (.-moveY state)))
                      :onPanResponderEnd  (fn []
                                            (js/setTimeout
                                             #(animated/set-value y 0)
                                             10))}))))

(def view
  (reagent/adapt-react-class
   (react/memo
    (fn [props]
      (let [{on-update-inset :onUpdateInset
             y               :y
             on-close        :onClose
             has-panel       :hasPanel
             bar-height      :barHeight
             children        :children}
            (bean/bean props)

            {:keys [keyboard-height
                    keyboard-max-height
                    keyboard-end-position]}
            (use-keyboard-dimension)
            {:keys [bottom]} (use-safe-area)

            kb-on-screen    (* -1 (- keyboard-height bottom tabbar-height))
            panel-on-screen (* -1 (- keyboard-max-height bottom tabbar-height))
            max-delta       (min 0 (if has-panel panel-on-screen kb-on-screen))
            end-position    (- keyboard-end-position (when has-panel keyboard-max-height))
            drag-diff       (animated/sub y end-position)
            delta-y         (animated/clamp (animated/add drag-diff panel-on-screen)
                                            max-delta
                                            0)
            panel-height    (+ (* -1 max-delta) bar-height)
            on-update       (react/callback
                             (fn []
                               (when on-update-inset
                                 (on-update-inset panel-height)))
                             [panel-height])]
        (react/effect! on-update)
        (animated/code!
         (fn []
           (when has-panel
             (animated/cond* (animated/greater-or-eq delta-y (* 0.25 max-delta))
                             [(animated/call* [] on-close)])))
         [delta-y has-panel on-close])
        (reagent/as-element
         (into [animated/view {:style {:position  :absolute
                                       :left      0
                                       :right     0
                                       :bottom    max-delta
                                       :height    panel-height
                                       :transform [{:translateY delta-y}]}}]
               (react/get-children children))))))))
