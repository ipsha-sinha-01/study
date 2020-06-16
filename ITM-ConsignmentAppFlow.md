# Consignment App


## Components : 


Logic App : 


1. "la-upload-shipment"

2. "la-create-order-shipment-relationship"

3. "la-create-consignment"


Function App "fa-util" functions : 


1. "group-orders-for-consignment"

2. "order-ready-for-consignment"

3. "add-order-release-tag"

4. "translate-consignment"
 


Azure Blob Storage : 

1. "shipments"

2. "gpsorders"

3. "consignments"


Service bus Queue : 

1. "q-shipment-received-from-otm"

2. "q-ready-for-consignment



Azure Table Storage : 

1. "orders"

2. "shipments"

3. "consignments"



## Consignment App Flow



![Image of Consignment Creation Flow](https://hclo365.sharepoint.com/:i:/r/sites/OTM_AzureTeam/Shared%20Documents/General/Helper-docs/ConsignmentApp-CreateConsignmentFlow.PNG?csf=1&web=1&e=IbIVio)





The above flow is achieved in below listed logic apps :


<br/><br/>
###  _"la-upload-shipment"_
<br/>


1. As soon as the Shipment is approved, OTM POSTs the Shipment XML to Logic app _"la-upload-shipment"_. 

2. Using domain name and Shipment Xid received in Shipment XML, Shipment ID String is generated . 

3. Shipment ID String is passed to OTM in HTTP GET request.
	
	- OTM responds back with  <shipmentID>.json

	- <shipmentID>.json is saved into Shipment Blob storage _"otm-shipments"_

4. A new event is published by event grid _"eg-itm"_.

5. Event is handled by Service Bus Queue _"q-shipment-received-from-otm"_, message is received in the queue.



<br/><br/>
### _"la-create-order-shipment-relationship"_
<br/>



1. As soon as the message is received in the queue _"q-shipment-received-from-otm"_, Logic app _"la-create-order-shipment-relationship"_ is triggered. 

2. Shipment Json File path is read from the message. Json File is loaded from blob storage _"otm-shipments"_.

3. Shipment json loaded from blob storage is sent in HTTP POST request to Function _"translate-shipment"_. 

4. The function transforms full form Shipment into Simplfied Shipment json.
	
5. For each Order in the Simplified Shipment

	- complete Order entity is loaded from _"Order"_ table. 

	- simplified shipment is added to list of Shipments in the Order entity
	
6. _Order-Shipment relationship_ defining json is created with Order and its associated List of _shipments_. The json is passed in HTTP POST request to function _"order-ready-for-consignment"_.
	
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
		

7. The function validates if all Shipments legs to fulfill the order from its source and destination are received. 

8. If the above function returns "YES", the status of Order is set to READY FOR CONSIGNMENT. 

9. Updated Order with Shipments array and Order Status is saved in _"Order"_ table storage. 

10.	Simplified Shipment json is passed in HTTP POST request to Function app _"shipment-order-relationship"_. 

	- A new _Shipment Order Relationship_ is created with Shipment having list of associated Orders. 
	
	Sample Shipment-Order relationship json
	
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

11.	Shipment with associated Order Ids is saved in Azure Table Storage _"Shipments"_.

12. The orders are passed to function _"group-orders-for-consignment"_ in HTTP POST request. The function groups the orders that have same payment term, currency, source, destination together into sub array.
	
	For example: 

	- [ [O1, O2], O3 ]
	
	In this case [O1, O2] are grouped together on the basis of same payment terms, currency, source and destination. O1 and O2 should go into one consignment and O3 into another consignment.    

12.	A new message is published to Service bus queue  _"q-ready-for-consignment"_ with list of orders whose status is READY FOR CONSIGNMENT.




<br/><br/>
### _"la-create-consignment"_
<br/>


1. As soon as the message is received in the queue _"q-ready-for-consignment"_, Logic app _"la-create-consignment"_ is triggered.

2. List of orders ready for consignments is read from the message received from "q-ready-for-consignment". 

3. For each group of orders in Orders array : 

	a) The first Order from Orders group is picked, it is verified if the Order already has Consignment ID in _"orders"_ table.
	
	- If not present, A new random Consignment Id is generated. 
	- If present, it represents that consignment is already created for this orders group. In this case, loop is continued for next group of Orders. 

	b) For the first Order, Full form Order json is loaded from Blob storage _"gps-orders"_. The Order json is copied to create new Consignment json. 

	c) In the Consignment json

		- Order Id is replaced with consignmentId, domain name is retained.
	
		- Ship unit array is emptied.
	
	
4. For each Order in group of Orders : 

	 - Full form Order json from Blob storage _"gps-orders"_ is loaded
	 
	 - Ship Units Array is extracted from Order json
	 
	 - Order Id in the Ship Units is replaced with consignmentId, domain name is retained.
		 
	 - Ship Units array is passed in HTTP POST request to Function app _"add-order-release-tag"_  
	 
	 - In the function, attribute "tag1" is set as Order's original order release Id. For each ship unit the an incrementing counter is appended to the following fields ( domain name is retained) 
	 
	   shipUnit->shipUnitBeanData-> shipUnitXid,shipUnitGid ;
	   
	   shipUnit->shipUnitBeanData->shipUnitLine->shipUnitBeanData->shipUnitGid, orderReleaseLineGid ;
	   
	   shipUnit->shipUnitBeanData->shipUnitLine->shipUnitBeanData->orderReleaseLine->orderReleaseLineBeanData-> orderReleaseLineGid, orderReleaseLineXid
	   
	   
	   
	 	 
	 - Updated Ship Units returned by the function are added to the Consignment json created in step 6 above. 


5. Consignment object with aggregated ship units is passed in HTTP POST request to function app _"translate-consignment"_. 

6. Function sums up the total weight, total volume, total new weight, total new volume, total packaging units, total ship units, total item package from the individual ship units. These values are set in header level attributes of Consignment json.

7.  HTTP POST request is made to OTM with the Consignment json. 

8.  Consignment json is saved into Azure Blon Storage _"consignments"_.

9.  Consignment with list of assocaiated Order Ids is saved in _"consignments"_ table. 

10.  Order is updated in _"Order"_ table with the consignment Id. 







	


