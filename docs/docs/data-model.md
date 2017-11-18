This page will describe the data model for Trident and Mercator.


Attribute | Description | Example
----------|-------------|-------------
name | template name |
managerDnsName | DNS name to be registered for the manager | %s.example.com |
managerSubjectAlternativeNames | comma-separated list of SAN names to be placed on the cert | *.example.com |



Attribute | Description | Example
----------|-------------|--------
awsAccount| AWS Account | name or account #
awsRegion | AWS Region  | us-east-1, us-west-1, etc.
awsManagerInstanceType | Manager Instance Type | m4.large, t2.large, etc.
awsManagerImageId| Manager AMI | ami-123456
awsManagerInstanceProfile | Manager Instance Role | manager-instance-role
awsManagerHostedZone | Hosted zone to use for manager DNS registration | 
awsManagerHostedZoneAccount | AWS account to use for DNS registration, if different from the swarm |
awsManagerSecurityGroups | Manager Security groups |
awsManagerSubnets | comma-separated list of subnets | subnet-1234, subnet-6655
awsWorkerInstanceType | worker instance type | m4.large, t2.large, etc.
awsWorkerImageId| worker AMI | ami-123456
awsWorkerInstanceProfile | worker Instance Role | manager-instance-role
awsWorkerSecurityGroups | worker Security groups |
awsWorkerSubnets | comma-separated list of subnets | subnet-1234, subnet-6655
