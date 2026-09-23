package com.caseware.pendingupdates;

import com.caseware.pendingupdates.model.ChangeRecord;
import com.caseware.pendingupdates.model.EngagementRef;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Idempotent DynamoDB-backed sink.
 *
 * <p>Table schema:
 * <ul>
 *   <li>PK: {@code firmId#engagementId}</li>
 *   <li>SK: {@code PENDING#<changeId>}</li>
 *   <li>Attributes: {@code region}, {@code changeId}, {@code templateId}</li>
 * </ul>
 *
 * <p>Conditional put ensures that replays are safe and that a change already
 * pending/applied/declined is not duplicated.
 */
public final class DynamoDbPendingUpdateSink implements PendingUpdateSink {

    private final DynamoDbClient client;
    private final String tableName;

    public DynamoDbPendingUpdateSink(DynamoDbClient client, String tableName) {
        this.client = Objects.requireNonNull(client, "client");
        this.tableName = Objects.requireNonNull(tableName, "tableName");
    }

    @Override
    public boolean markPending(EngagementRef engagement, ChangeRecord change, String region) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("pk", AttributeValue.fromS(engagement.firmId() + "#" + engagement.engagementId()));
        item.put("sk", AttributeValue.fromS("PENDING#" + change.changeId()));
        item.put("region", AttributeValue.fromS(region));
        item.put("changeId", AttributeValue.fromS(change.changeId()));
        item.put("templateId", AttributeValue.fromS(change.templateId()));

        PutItemRequest request = PutItemRequest.builder()
                .tableName(tableName)
                .item(item)
                .conditionExpression("attribute_not_exists(pk) AND attribute_not_exists(sk)")
                .build();

        try {
            client.putItem(request);
            return true;
        } catch (ConditionalCheckFailedException e) {
            return false;
        }
    }
}