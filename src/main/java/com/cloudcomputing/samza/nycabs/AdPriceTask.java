package com.cloudcomputing.samza.nycabs;

import com.google.common.io.Resources;
import org.apache.samza.context.Context;
import org.apache.samza.storage.kv.KeyValueStore;
import org.apache.samza.system.IncomingMessageEnvelope;
import org.apache.samza.system.OutgoingMessageEnvelope;
import org.apache.samza.task.InitableTask;
import org.apache.samza.task.MessageCollector;
import org.apache.samza.task.StreamTask;
import org.apache.samza.task.TaskCoordinator;

import org.codehaus.jackson.map.ObjectMapper;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Consumes the stream of ad-click.
 * Outputs a stream which handles static file and one stream
 * and gives a stream of revenue distribution.
 */
public class AdPriceTask implements StreamTask, InitableTask {

    /*
       Define per task state here. (kv stores etc)
       READ Samza API part in Writeup to understand how to start
    */
    private KeyValueStore<String, Map<String, Object>> storeAds;
    ObjectMapper mapper = new ObjectMapper();

    @Override
    @SuppressWarnings("unchecked")
    public void init(Context context) throws Exception {
        // Initialize (maybe kv store and static data?)
        storeAds = (KeyValueStore<String, Map<String, Object>>) context.getTaskContext().getStore("store-ads");
        //Initialize static data and save them in kv store
        initialize("NYCstoreAds.json");
    }

    /**
     * This function will read the static data from resources folder
     * and save data in KV store.
     */
    public void initialize(String businessFile) {
        List<String> businessRawString = AdPriceConfig.readFile(businessFile);
        System.out.println("Reading store info file from " + Resources.getResource(businessFile).toString());
        System.out.println("Store raw string size: " + businessRawString.size());

        for (String rawString : businessRawString) {
            Map<String, Object> mapResult;
            ObjectMapper mapper = new ObjectMapper();
            try {
                mapResult = mapper.readValue(rawString, HashMap.class);
                String storeId = (String) mapResult.get("storeId");
                storeAds.put(storeId, mapResult);
            } catch (Exception e) {
                System.out.println("Failed at parse store info :" + rawString);
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void process(IncomingMessageEnvelope envelope, MessageCollector collector,
                        TaskCoordinator coordinator) throws Exception {
        /*
        All the messsages are partitioned by userId, which means the messages
        sharing the same userId will arrive at the same task, similar to the
        approach that MapReduce sends all the key value pairs with the same key
        into the same reducer.
        */

        String incomingStream = envelope.getSystemStreamPartition().getStream();
        Map<String,Object> adClickData = (Map<String, Object>)envelope.getMessage();
        System.out.println("AdPriceTask.process. reaching here " + adClickData.toString());
        if (incomingStream.equals(AdPriceConfig.AD_CLICK_STREAM.getStream())) {
            System.out.println("Inside If condition \n\n\n");
            System.out.println("StoreAds+"+ storeAds.toString());
            int userId = (Integer) adClickData.get("userId");
            String storeId = (String) adClickData.get("storeId");
            boolean clicked = Boolean.parseBoolean((String) adClickData.get("clicked"));

            //Get AdPrice from the KV store
            Map<String,Object> storeInfo =  storeAds.get("storeId");
            if (storeInfo == null) return;
            int adPrice = (Integer) storeInfo.get("adPrice");

            //Calculate Ad Revenue
            int adCompanyRevenue, cabRevenue;
            if (clicked) {
                adCompanyRevenue = (int) (adPrice * 0.8);
                cabRevenue = (int) (adPrice * 0.2);
            } else {
                adCompanyRevenue = (int) (adPrice * 0.5);
                cabRevenue = (int) (adPrice * 0.5);
            }

            // Create output message
            Map<String, Object> output= new HashMap<>();
            output.put("userId", userId);
            output.put("storeId", storeId);
            output.put("ad", adCompanyRevenue);
            output.put("cab", cabRevenue);

            System.out.println("AdPrice Output reaching here \n\n\n\n" + output.toString());
            // Send message to output stream
            collector.send(new OutgoingMessageEnvelope(
                    AdPriceConfig.AD_PRICE_STREAM,
                    mapper.readTree(mapper.writeValueAsString(output))
            ));
        } else {
            throw new IllegalStateException("Unexpected input stream: " + envelope.getSystemStreamPartition());
        }
    }
}
