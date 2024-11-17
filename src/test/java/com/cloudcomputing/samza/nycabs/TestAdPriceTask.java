package com.cloudcomputing.samza.nycabs;


import com.cloudcomputing.samza.nycabs.application.AdPriceTaskApplication;
import org.apache.samza.serializers.NoOpSerde;
import org.apache.samza.test.framework.TestRunner;
import org.apache.samza.test.framework.system.descriptors.InMemoryInputDescriptor;
import org.apache.samza.test.framework.system.descriptors.InMemoryOutputDescriptor;
import org.apache.samza.test.framework.system.descriptors.InMemorySystemDescriptor;
import org.codehaus.jackson.map.ObjectMapper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.ListIterator;
import java.util.Map;


public class TestAdPriceTask {
    ObjectMapper mapper = new ObjectMapper();
    Map<String, String> confMap = new HashMap<>();
    InMemorySystemDescriptor isd;
    InMemoryInputDescriptor adClickEvent;
    InMemoryOutputDescriptor adPriceEvent;

    @Before public void setup(){
        confMap.put("stores.store-ads.factory", "org.apache.samza.storage.kv.RocksDbKeyValueStorageEngineFactory");
        confMap.put("stores.store-ads.key.serde", "string");
        confMap.put("stores.store-ads.msg.serde", "json");
        confMap.put("serializers.registry.json.class", "org.apache.samza.serializers.JsonSerdeFactory");
        confMap.put("serializers.registry.string.class", "org.apache.samza.serializers.StringSerdeFactory");
        confMap.put("serializers.registry.integer.class", "org.apache.samza.serializers.IntegerSerdeFactory");

        isd = new InMemorySystemDescriptor("kafka");
        adClickEvent = isd.getInputDescriptor("ad-click", new NoOpSerde<>());
        adPriceEvent = isd.getOutputDescriptor("ad-price", new NoOpSerde<>());

    }

    @Test
    public void testAdPriceClicked() throws Exception {
        TestRunner
                .of(new AdPriceTaskApplication())
                .addInputStream(adClickEvent, TestUtils.genStreamData("adClicked"))
                .addOutputStream(adPriceEvent, 1)
                .addConfig(confMap)
                .addConfig("deploy.test", "true")
                .run(Duration.ofSeconds(7));

        Assert.assertEquals(2, TestRunner.consumeStream(adPriceEvent, Duration.ofSeconds(7)).get(0).size());
        ListIterator<Object> resultIter = TestRunner.consumeStream(adPriceEvent, Duration.ofSeconds(7)).get(0).listIterator();

        String ad1 = "{\"userId\":13617,\"ad\":800,\"cab\":200,\"storeId\":\"44SY464xDHbvOcjDzRbKkQ\"}";
        Assert.assertEquals(mapper.readTree(ad1), resultIter.next());

        String ad2 = "{\"userId\":8197,\"ad\":400,\"cab\":100,\"storeId\":\"WHRHK3S1mQc3PmhwsGRvbw\"}";
        Assert.assertEquals(mapper.readTree(ad2), resultIter.next());
    }

    @Test
    public void testAdPriceNotClicked() throws Exception {
        TestRunner
                .of(new AdPriceTaskApplication())
                .addInputStream(adClickEvent, TestUtils.genStreamData("adNotClicked"))
                .addOutputStream(adPriceEvent, 1)
                .addConfig(confMap)
                .addConfig("deploy.test", "true")
                .run(Duration.ofSeconds(7));

        Assert.assertEquals(2, TestRunner.consumeStream(adPriceEvent, Duration.ofSeconds(7)).get(0).size());
        ListIterator<Object> resultIter = TestRunner.consumeStream(adPriceEvent, Duration.ofSeconds(7)).get(0).listIterator();

        String ad1 = "{\"userId\":19036,\"ad\":500,\"cab\":500,\"storeId\":\"44SY464xDHbvOcjDzRbKkQ\"}";
        Assert.assertEquals(mapper.readTree(ad1), resultIter.next());

        String ad2 = "{\"userId\":18047,\"ad\":200,\"cab\":200,\"storeId\":\"V7lXZKBDzScDeGB8JmnzSA\"}";
        Assert.assertEquals(mapper.readTree(ad2), resultIter.next());
    }
}
