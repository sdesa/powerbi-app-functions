# What is powerbi-app-functions?

This sample is an azure function that gets the embed token and report details to embed a power bi report in an application.

# Prerequisite

This function can run by itself, but is currently accompanied with another repo called [powerbi-app](https://github.com/sdesa/powerbi-app) to support fetching the token and the report details for the angular application.

# Blog 

This source code is also accompanied by a blog that you can access here which has additional setup instructions to run this function with the angular application.

# How to run this sample

Running instructions:

1. Replace the reportId and groupId variables in FunctionTest.java to make sure your unit tests pass. These are the valid report Ids and group Id that are part of the workspace that this service principal has access to.
2. Replace the service principal details in Function.java with your SP details.
3. If you are using VS Code, you can run Ctrl + F5 to run this.

# References

https://docs.microsoft.com/en-us/power-bi/developer/embedded/embed-sample-for-customers
https://github.com/microsoft/PowerBI-Developer-Samples/tree/master/Java
