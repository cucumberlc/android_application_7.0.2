/*
 * Copyright © 2023 Silicon Labs, http://www.silabs.com. All rights reserved.
 */

package com.siliconlabs.bluetoothmesh.App.Logic.InteroperabilityTests

import androidx.annotation.StringRes
import com.siliconlabs.bluetoothmesh.App.MeshApplication
import com.siliconlabs.bluetoothmesh.R

enum class IOPTestIdentity(
        val specificationOrdinalNumber: String,
        @StringRes private val categoryRes: Int,
        @StringRes private val descriptionRes: Int
) {
    BeaconingProxyNode(
            "1.1",
            R.string.iop_test_category_beaconing,
            R.string.iop_test_description_beaconing_gatt_low_latency
    ),
    BeaconingRelayNode(
            "1.2",
            R.string.iop_test_category_beaconing,
            R.string.iop_test_description_beaconing_adv_gatt_low_latency
    ),
    BeaconingFriendNode(
            "1.3",
            R.string.iop_test_category_beaconing,
            R.string.iop_test_description_beaconing_adv_gatt_low_power
    ),
    BeaconingLpnNode(
            "1.4",
            R.string.iop_test_category_beaconing,
            R.string.iop_test_description_beaconing_adv_gatt_balanced
    ),

    ProvisioningProxyNode(
            "1.5",
            R.string.iop_test_category_provisioning,
            R.string.iop_test_description_provisioning_no_oob
    ),
    ProvisioningRelayNode(
            "1.6",
            R.string.iop_test_category_provisioning,
            R.string.iop_test_description_provisioning_static_oob
    ),
    ProvisioningFriendNode(
            "1.7",
            R.string.iop_test_category_provisioning,
            R.string.iop_test_description_provisioning_output_oob
    ),
    ProvisioningLpnNode(
            "1.8",
            R.string.iop_test_category_provisioning,
            R.string.iop_test_description_provisioning_input_oob
    ),

    UnicastControlAckProxyNode(
            "2.1",
            R.string.iop_test_category_unicast_control,
            R.string.iop_test_description_unicast_control_proxy_ack
    ),
    UnicastControlNonAckProxyNode(
            "2.2",
            R.string.iop_test_category_unicast_control,
            R.string.iop_test_description_unicast_control_proxy_no_ack
    ),
    UnicastControlAckRelayNode(
            "2.3",
            R.string.iop_test_category_unicast_control,
            R.string.iop_test_description_unicast_control_relay_ack
    ),
    UnicastControlNonAckRelayNode(
            "2.4",
            R.string.iop_test_category_unicast_control,
            R.string.iop_test_description_unicast_control_relay_no_ack
    ),
    UnicastControlAckFriendNode(
            "2.5",
            R.string.iop_test_category_unicast_control,
            R.string.iop_test_description_unicast_control_friend_ack
    ),
    UnicastControlNonAckFriendNode(
            "2.6",
            R.string.iop_test_category_unicast_control,
            R.string.iop_test_description_unicast_control_friend_no_ack
    ),
    UnicastControlAckLpnNode(
            "2.7",
            R.string.iop_test_category_unicast_control,
            R.string.iop_test_description_unicast_control_lpn_ack
    ),
    UnicastControlNonAckLpnNode(
            "2.8",
            R.string.iop_test_category_unicast_control,
            R.string.iop_test_description_unicast_control_lpn_no_ack
    ),

    MulticastControlNodes(
            "2.9",
            R.string.iop_test_category_multicast_control,
            R.string.iop_test_description_multicast_control
    ),

    RemoveNodesFromNetwork(
            "3.1",
            R.string.iop_test_category_remove_nodes,
            R.string.iop_test_description_remove_node
    ),
    AddNodeToNetwork(
            "3.2",
            R.string.iop_test_category_add_nodes,
            R.string.iop_test_description_add_node
    ),

    ConnectingNetwork(
            "3.3",
            R.string.iop_test_category_connection,
            R.string.iop_test_description_connection
    ),

    PostTesting(
            "3.4",
            R.string.iop_test_category_post_testing,
            R.string.iop_test_description_remove_all_nodes
    );

    val title get() = "Test $specificationOrdinalNumber - ${MeshApplication.appContext.getString(categoryRes)}"
    val description get() = MeshApplication.appContext.getString(descriptionRes)
}
