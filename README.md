# Machine printing and eveloping plugin
Project Laurentius-meps-plugin is [Laurentius](https://github.com/VsrsCif/Laurentius)  plugin 
for using Machine printing and eveloping service. 

## User case:
Client (service requester) send mail (envelope data and contents in pdf form) to service provider.
Service provider (at least once a day) print and envelope all received requests. 
Enveloped mail is than submitted to local post for delivery and status report is 
submitted to service requester all requests (PROCESSED, DELETED). 

![MEPS service sequence diagram](https://github.com/VsrsCif/Laurentius-meps-plugin/docs/images/meps-actions.png)


###Client (service requester)
Plugin on client side (service requester) plugin validate envelope data and concatenate
pdfs. Because both side printing is presumed, concatenation adds blank page to all 
documents with odd page count.


###Service provider
Plugin on service provider validates received mail (Validates envelope data and counts pdf pages (test if pdf is readable)).
For service provider plugin has two "cron tasks".
 * MEPSTask: create daily package for machine printing and enveloping
 * MEPSStatusSubmitter: create and submit status report for all processed requests to requestor.



### Supported services
MEPS Service type correspond to mail envelope type and postal delivery service.



| Service type  | description | envelope exampe
| ------------- | ------------- |
| PrintAndEnvelope-C5      | Ordinal mail with C5 envelope (1g - 2kg)|   |
| PrintAndEnvelope-C5-R    | Registred mail with: C5 envelope (1g - 2kg) |  |
| PrintAndEnvelope-C5-AD   | Registred  mail with AdviceOfDelivery  (1g - 2kg) | |
| PrintAndEnvelope-Package | Package  (2kg - 30kg) | |
| PrintAndEnvelope-Package_R | Registred package  (2kg - 30kg) | |
| PrintAndEnvelope-Package_AD| Registred package with AdviceOfDelivery  (2kg - 30kg) | |

| PrintAndEnvelope-LegalZPP  | Legal service in civil procedures - personal (1g - 2kg)| [ZPP envelope (personal)](https://github.com/VsrsCif/Laurentius-meps-plugin/docs/ZPP_osebno.pdf | 
| PrintAndEnvelope-LegalZPP_NP | Legal service in civil procedures  (1g - 2kg)|   [ZPP envelope ](https://github.com/VsrsCif/Laurentius-meps-plugin/docs/ZPP_navadno.pdf |
| PrintAndEnvelope-LegalZUP |  Legal service in administrative procedures (1g - 2kg)|   [ZUP envelope ](https://github.com/VsrsCif/Laurentius-meps-plugin/docs/ZUP.pdf |


### Prerequisites
Install Laurentius


## Getting Started
1. Install plugin to Laurentius
2. In plugin settings register new plugin services and pmodes
3. Add service provider or service requestor to pmodes
4. start sending / recieving mail for MEPS.

