/**
 * Copyright © 2016-2018 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.rule.engine.telemetry;

import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.thingsboard.rule.engine.TbNodeUtils;
import org.thingsboard.rule.engine.api.RuleNode;
import org.thingsboard.rule.engine.api.TbContext;
import org.thingsboard.rule.engine.api.TbNode;
import org.thingsboard.rule.engine.api.TbNodeConfiguration;
import org.thingsboard.rule.engine.api.TbNodeException;
import org.thingsboard.server.common.data.kv.BasicTsKvEntry;
import org.thingsboard.server.common.data.kv.KvEntry;
import org.thingsboard.server.common.data.kv.TsKvEntry;
import org.thingsboard.server.common.data.plugin.ComponentType;
import org.thingsboard.server.common.msg.TbMsg;
import org.thingsboard.server.common.msg.core.TelemetryUploadRequest;
import org.thingsboard.server.common.transport.adaptor.JsonConverter;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@RuleNode(
        type = ComponentType.ACTION,
        name = "save timeseries data",
        configClazz = TbMsgTelemetryNodeConfiguration.class,
        nodeDescription = "Saves timeseries data",
        nodeDetails = "Saves timeseries telemetry data based on configurable TTL parameter. Expects messages with 'POST_TELEMETRY' message type",
        uiResources = {"static/rulenode/rulenode-core-config.js", "static/rulenode/rulenode-core-config.css"},
        configDirective = "tbActionNodeTelemetryConfig"
)

public class TbMsgTelemetryNode implements TbNode {

    private TbMsgTelemetryNodeConfiguration config;

    @Override
    public void init(TbContext ctx, TbNodeConfiguration configuration) throws TbNodeException {
        this.config = TbNodeUtils.convert(configuration, TbMsgTelemetryNodeConfiguration.class);
    }

    @Override
    public void onMsg(TbContext ctx, TbMsg msg) {
        if (!msg.getType().equals("POST_TELEMETRY")) {
            ctx.tellError(msg, new IllegalArgumentException("Unsupported msg type: " + msg.getType()));
            return;
        }

        String src = new String(msg.getData(), StandardCharsets.UTF_8);
        TelemetryUploadRequest telemetryUploadRequest = JsonConverter.convertToTelemetry(new JsonParser().parse(src));
        Map<Long, List<KvEntry>> tsKvMap = telemetryUploadRequest.getData();
        if (tsKvMap == null) {
            ctx.tellError(msg, new IllegalArgumentException("Msg body us empty: " + src));
            return;
        }
        List<TsKvEntry> tsKvEntryList = new ArrayList<>();
        for (Map.Entry<Long, List<KvEntry>> tsKvEntry : tsKvMap.entrySet()) {
            for (KvEntry kvEntry : tsKvEntry.getValue()) {
                tsKvEntryList.add(new BasicTsKvEntry(tsKvEntry.getKey(), kvEntry));
            }
        }
        String ttlValue = msg.getMetaData().getValue("TTL");
        long ttl = !StringUtils.isEmpty(ttlValue) ? Long.valueOf(ttlValue) : config.getDefaultTTL();
        ctx.getTelemetryService().saveAndNotify(msg.getOriginator(), tsKvEntryList, ttl, new TelemetryNodeCallback(ctx, msg));
    }

    @Override
    public void destroy() {
    }

}
