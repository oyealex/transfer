package com.ecommerce.logistics.entity;

/**
 * Represents the lifecycle status of a shipment.
 *
 * <p>Valid progression:
 * CREATED -&gt; PICKING -&gt; LABEL_PRINTED -&gt; OUTBOUND -&gt; COLLECTED
 * -&gt; IN_TRANSIT -&gt; DELIVERED, with EXCEPTION possible at any stage.
 */
public enum ShipmentStatus {

    /** Shipment has been created, awaiting warehouse processing */
    CREATED,
    /** Pick list has been generated, picking in progress */
    PICKING,
    /** Shipping label has been printed */
    LABEL_PRINTED,
    /** Shipment has been scanned and left the warehouse */
    OUTBOUND,
    /** Carrier has collected the package */
    COLLECTED,
    /** Package is in transit */
    IN_TRANSIT,
    /** Package has been delivered to the recipient */
    DELIVERED,
    /** An exception occurred during shipping */
    EXCEPTION
}
