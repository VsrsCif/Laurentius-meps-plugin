# Machine printing and enveloping plugin
Project Laurentius-meps-plugin is [Laurentius](https://github.com/VsrsCif/Laurentius)  plugin 
for using Machine printing and eveloping service. 

## Usecase:
Client (service requester) send mail (envelope data and contents in pdf form) to service provider.
Service provider (at least once a day) print and envelope all received requests. Printed and enveloped mail is delivered to post office for physical delivery. After that
status report is submitted to service requester for all requests (mail) (PROCESSED, DELETED). 

![MEPS service sequence diagram](https://github.com/VsrsCif/Laurentius-meps-plugin/blob/master/docs/images/meps-actions.png)


## Supported services
MEPS Service type correspond to mail envelope type and postal delivery service.

| Service type  | description | envelope exampe |
| ------------- | ------------- | ------------- |
| PrintAndEnvelope-C5      | Ordinal mail with C5 envelope (1g - 2kg)|  [Envelope](https://github.com/VsrsCif/Laurentius-meps-plugin/blob/master/docs/Envelope-ordinar.png) |
| PrintAndEnvelope-C5-R    | Registred mail with: C5 envelope (1g - 2kg) | [Envelope with UPN](https://github.com/VsrsCif/Laurentius-meps-plugin/blob/master/docs/Envelope-ordinar-UPN.png)  |
| PrintAndEnvelope-C5-AD   | Registred  mail with AdviceOfDelivery  (1g - 2kg) | |
| PrintAndEnvelope-Package | Package  (2kg - 30kg) | |
| PrintAndEnvelope-Package_R | Registred package  (2kg - 30kg) | |
| PrintAndEnvelope-Package_AD| Registred package with AdviceOfDelivery  (2kg - 30kg) | |
| PrintAndEnvelope-LegalZPP  | Legal service in civil procedures - personal (1g - 2kg) | [Printed ZPP envelope (personal)](https://github.com/VsrsCif/Laurentius-meps-plugin/blob/master/docs/ZPP_Osebna_printed.png), [ZPP envelope (personal)](https://github.com/VsrsCif/Laurentius-meps-plugin/blob/master/docs/ZPP_osebno.pdf) | 
| PrintAndEnvelope-LegalZPP_NP | Legal service in civil procedures  (1g - 2kg)| [ Printed ZPP envelope ]( https://github.com/VsrsCif/Laurentius-meps-plugin/blob/master/docs/ZPP_Navadna_printed.png),  [ZPP envelope ]( https://github.com/VsrsCif/Laurentius-meps-plugin/blob/master/docs/ZPP_navadno.pdf) |
| PrintAndEnvelope-LegalZUP |  Legal service in administrative procedures (1g - 2kg)|   [ZUP envelope ](https://github.com/VsrsCif/Laurentius-meps-plugin/blob/master/docs/ZUP.pdf) |


## Interceptors
Interceptors are Laurentius plugins for intercept and process out/in mail during transmission. 
### MEPSOutInterceptor
Use of interceptor is in service requester side. For action "AddMAil" interceptor validates and 
concatenate all pdf's to one pdf as printable content for mail. 
"Concatenation" adds blank page to all pdf documents with odd number of pages. 
(Printing is on both side of sheet of paper. Blank page ensures that each pdf document starts on new sheet of paper.
Only "concenated PDF" and envelope data are sent to service provider.

Other actions are ignored!

### MEPSInInterceptor
Use of interceptor is on both side (service requester/ service provider). 
For "service provider" on action "AddMail" interceptor validates envelope data and pdf content.
If envelope data and "printable content" are valid AS4 Receipt is returned, else fault (with cause description) is triggered. 
For "service requester" on action "StatusReport" updates all out mail statuses.


## Cron task
Plugin contains two cron tasks for service provider:
 * MEPSTask: create daily package for machine printing and enveloping
 * MEPSStatusSubmitter: create and submit status report for all processed requests to requestor.



### Prerequisites
Install Laurentius


## Getting Started
1. Install plugin to Laurentius
2. In plugin settings register new plugin services and pmodes
3. Add service provider or service requestor to pmodes
4. start sending / recieving mail for MEPS.

