# Consignment App


## Components : 


1. Logic App : "la-upload-shipment"

2. Logic App : "la-create-order-shipment-relationship"

3. Logic App : "la-create-consignment"

4. Logic App : "la-upload-shipment"

5. Function App : "group-orders-for-consignment"

6. Function App : "order-ready-for-consignment"

7. Function App : "add-order-release-tag"

8. Function App : "translate-consignment"
 
9. Azure Blob Storage : "shipments"

10. Azure Blob Storage : "gpsorders"

11. Azure Blob Storage : "consignments"

12. Service bus Queue : "q-shipment-received-from-otm"

13. Service bus Queue : "q-ready-for-consignment

14. Azure Table Storage : "orders"

15. Azure Table Storage : "shipments"

16. Azure Table Storage : "consignments"


## Consignment App Flow


![Image of Consignment Creation Flow](https://hclo365.sharepoint.com/:i:/r/sites/OTM_AzureTeam/Shared%20Documents/General/Helper-docs/ConsignmentApp-CreateConsignmentFlow.PNG?csf=1&web=1&e=IbIVio)





The above flow is achieved in below listed logic apps :



### "la-upload-shipment"



1. As soon as the Shipment is approved, OTM POSTs the Shipment XML to Logic app "la-upload-shipment". 

2. Using using domain name and Shipment Xid received in Shipment XML, Shipment ID String is generated . 

3. Shipment ID String is passed to OTM in HTTP GET request.
	
	- OTM responds back with  <shipmentID>.json

	- <shipmentID>.json is saved into Shipment Blob storage _"shipments"_

4. A new event is published by event grid _"eg-itm"_.

5. Event is handled by Service Bus Queue _"q-shipment-received-from-otm"_, message is received in the queue.




### "la-create-order-shipment-relationship"



1. As soon as the message is received in the queue _"q-shipment-received-from-otm"_, Logic app _"la-create-order-shipment-relationship"_ is triggered. 

2. Shipment Json File path is read from the message. Json File is loaded from blob storage _"shipments"_.

3. Shipment json loaded from blob storage is sent in HTTP POST request to Function app _"translate-shipment"_. 

4. The function transforms full form Shipment into Simplfied Shipment json. The translated shipment contains an array of Orders having Order Id, Shipment Id, Shipment Source, Shipment destination. 
	
	Sample Simplified Shipment with two orders : 
	
		[
			  {
				"orderId": "I.ITM-B9264A205ECC-001",
				"shipmentId": "I.01264",
				"source": "I.18748-SUP-8",
				"destination": "I.310-DT-1"
			  },
			  {
				"orderId": "I.ITM-B9264A205ECC-002",
				"shipmentId": "I.01264",
				"source": "I.18748-SUP-8",
				"destination": "I.310-DT-1"
			  }
		]

5. For each Order in the Simplified Shipment

	- complete Order entity is loaded from _"Order"_ table. 

	- simplified shipment is added to list of Shipments in the Order entity
	
6. _Order-Shipment relationship_ defining json is created with Order and its associated List of _shipments_
	
	 Sample Order-Shipment relationship json
	
		{
		  "orderId": "I.ITM-B9264A205ECC-001",
		  "destination": "I.310-DT-1", (orderDestination)
		  "source": "I.18748-SUP-8", (orderSource)
		  "shipments": [
				{
				  "orderId": "I.ITM-B9264A205ECC-001",
				  "shipmentId": "I.01264",
				  "source": "I.18748-SUP-8", (shipmentSource)
				  "destination": "I.310-DT-1" (shipmentDestination)
				}
			]
		}
		
7. Order-Shipment relationship json is sent in HTTP POST request to function app _"order-ready-for-consignment"_. 

8. The function app validates if all Shipments legs to fulfill the order from its source and destination are received. 

9. If the above function returns "YES", the status of Order is set to READY FOR CONSIGNMENT. 

8. Updated Order with Shipments array and Order Status is saved in _"Order"_ table storage. 

9. Simplified Shipment json is passed in HTTP POST request to Function app _"shipment-order-relationship"_. 

	- A new _Shipment Order Relationship_ is created with Shipment having list of associated Orders. 
	
	Sample Shipmnet-Order relationship json
	
		{
		  "shipmentId": "I.01264",
		  "orders": [
				{
				  "orderId": "I.ITM-B9264A205ECC-001"
				},
				{
				  "orderId": "I.ITM-B9264A205ECC-002"
				}
			  ]
		}

10.  Shipments with associated Order Ids is saved in Azure Table Storage _"Shipments"_.

11.  A new message is published to Service bus queue  _"q-ready-for-consignment"_ with list of orders whose status is READY FOR CONSIGNMENT.



### "la-create-consignment"


1. As soon as the message is received in the queue _"q-ready-for-consignment"_, Logic app _"la-create-consignment"_ is triggered.

2. List of orders ready for consignments is read from the message received from "q-ready-for-consignment". 

3. For the first Order from Orders List, if the Order has Consignment ID already populated is verified.
	
4. If yes, A new random Consignment Id <CSM><Random><Counter> is created. 

5. For the first Order, Full form Order json from Blob storage _"orders"_ is loaded. The Order json is copied to create new Consignment json. 

6. In the Consignment json

	- Order Id is replaced with <consignmentId>, domain name is retained.
	
	- Ship unit array is emptied.
	
	
7. For each Order in List of Orders : 

	 - Full form Order json from Blob storage _"orders"_ is loaded
	 
	 - Ship Units Array is extracted from Order json
	 
	 - Order Id in the Ship Units is replaced with <consignmentId>, domain name is retained
		 
	 - Ship Units array is passed in HTTP POST request to Function app _"add-order-release-tag"_  
	 
	 - The function adds attribute "tag1" in order release line of the ship unit as Order's original order release Id. A new counter 	    is created. For each ship unit the counter incremented and set in the following fields - 
	 
	   shipUnit->shipUnitBeanData-> shipUnitXid,shipUnitGid ;
	   
	   shipUnit->shipUnitBeanData->shipUnitLine->shipUnitBeanData->shipUnitGid, orderReleaseLineGid ;
	   
	   shipUnit->shipUnitBeanData->shipUnitLine->shipUnitBeanData->orderReleaseLine->orderReleaseLineBeanData-> orderReleaseLineGid, orderReleaseLineXid
	   
	   
	   Domain name is retained whereever applicable. 
	   
	   
	 	 
	 - Updated Ship Units returned by the function are added to the Consignment json created in step 6 above. 


8. Consignment object with aggregated ship units is passed in HTTP POST request to function app _"translate-consignment"_. 

9. Function sums up the total weight, total volume, total new weight, total new volume, total packaging units, total ship units, total item package from the individual ship units. These values are set in header level attributes of Consignment json.

10. HTTP POST request is made to OTM with the Consignment json. 

11. Consignment json is saved into Azure Blon Storage _"consignments"_.

12. Consignment with list of assocaiated Order Ids is saved in _"consignments"_ table. 

13. Order is updated in _"Order"_ table with the consignment Id. 







	


