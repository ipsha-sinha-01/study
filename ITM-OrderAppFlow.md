# Order App


## Components : 


1. Order html
 
2. Azure Blob Storage : "template"

2. Azure Blob Storage : "gpsorders"

3. Event grid : "eg-itm"

4. Service bus Queue : "q-new-order-file"

5. Logic App : "la-post-order"

6. Function App : "fa-util"
	Function : "transform-order"
	
7. Azure Table Storage : "orders"

## Order App Flow


![Image of Order App Flow](https://hclo365.sharepoint.com/:i:/r/sites/OTM_AzureTeam/Shared%20Documents/General/Helper-docs/OrderAppFlow.PNG?csf=1&web=1&e=BhAuhq)



1. User opens _"Order"_ page and enters/updates the order related attributes. 
	- Order html displays some default Order attributes using templates stored in Azure blob storage _"itmstore/template"._
	
2. Created Order is stored in json format under blob storage _"itmstore/gpsorders"_.

3. A new event is published by event grid _"eg-itm"_.

4. Event is handled by Service Bus Queue _"q-new-order-file"_, message is received in the queue.

5. As soon as the message is received in the queue, Logic app _"la-post-order" is triggered. 
	- The file path for order json file is read from the message.
	
	- File content is loaded from the path.
	
	- POST HTTP request is sent to _"OTM"_ with Order json in request body.
	
	- After successful response from _"OTM"_, a new POST HTTP request is sent to function app _"fa-util"_ function _"translate-order"_

7. Function _"translate-order"_ flattens the Order into simplified Order with attributes as Order ID, Payment Terms, Currency, Source Location, and Destination Location. 

8. "la-post-order" receives the simplfied Order and saves into Azure storage table _"itmstore/orders"_
