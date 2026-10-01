package com.partimo.domain.model

/** Accessibilità in sedia a rotelle dichiarata dalla fonte (es. il tag `wheelchair` di OpenStreetMap). */
enum class WheelchairAccess {
    /** Accessibile: ingresso senza gradini e ambienti principali raggiungibili. */
    YES,

    /** In parte: per esempio un gradino all'ingresso o solo alcune sale. */
    LIMITED,
    NO,
}
