(ns status-im.acquisition.core
  (:require [re-frame.core :as re-frame]
            [status-im.utils.fx :as fx]
            [status-im.ethereum.json-rpc :as json-rpc]
            [status-im.waku.core :as waku]
            [status-im.utils.types :as types]
            [status-im.utils.platform :as platform]
            [status-im.ethereum.core :as ethereum]
            ["react-native-device-info" :refer [getInstallReferrer]]))

(def acquisition-gateway "https://get.status.im")

(def acquisition-routes {:clicks        (str acquisition-gateway "/clicks")
                         :registrations (str acquisition-gateway "/registrations")})

(fx/defn handle-error
  {:events [::on-error]}
  [_ error]
  (prn error)
  {:utils/show-popup {:title   "Request failed"
                      :content (str error)}})

(fx/defn handle-acquisition
  {:events [::handle-acquisition]}
  [{:keys [db] :as cofx} {:keys [message on-success]}]
  (let [msg (types/clj->json message)]
    {::json-rpc/call [{:method     (json-rpc/call-ext-method (waku/enabled? cofx) "signMessageWithChatKey")
                       :params     [msg]
                       :on-error   #(re-frame/dispatch [::on-error "Could not sign message"])
                       :on-success #(re-frame/dispatch [::call-acquisition-gateway
                                                        {:chat-key   (get-in db [:multiaccount :public-key])
                                                         :message    msg
                                                         :on-success on-success} %])}]}))

(re-frame/reg-fx
 ::get-referrer
 (fn []
   (when platform/android?
     (-> (getInstallReferrer)
         (.then (fn [referrer]
                  (re-frame/dispatch [::has-referrer referrer])))))))

(fx/defn referrer-registered
  {:events [::referrer-registered]}
  [{:keys [db]}]
  {:db db})

(fx/defn has-referrer
  {:events [::has-referrer]}
  [{:keys [db] :as cofx} referrer]
  (let [payload {:chat_key    (get-in db [:multiaccount :public-key])
                 :address     (ethereum/default-address db)
                 :invite_Code referrer}]
    (handle-acquisition cofx {:message    payload
                              :type       :clicks
                              :on-success ::referrer-registered})))

(fx/defn app-setup
  {}
  [_]
  {::get-referrer nil})

(fx/defn call-acquisition-gateway
  {:events [::call-acquisition-gateway]}
  [cofx
   {:keys [chat-key message on-success type]
    :or   {type :registrations}}
   sig]
  (let [payload {:chat_key chat-key
                 :msg      message
                 :sig      sig
                 :version  2}]
    {:http-post {:url                   (get acquisition-routes type)
                 :opts                  {:headers {"Content-Type" "application/json"}}
                 :data                  (types/clj->json payload)
                 :success-event-creator (fn [response]
                                          [on-success (types/json->clj (get response :response-body))])
                 :failure-event-creator (fn [error]
                                          [::on-error (types/json->clj error)])}}))
