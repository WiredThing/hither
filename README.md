[![Build Status](https://travis-ci.org/WiredThing/hither.svg?branch=master)](https://travis-ci.org/WiredThing/hither)

docker-registry
===============

A docker private registry implementation using Play Framework 2.3 and Scala


## Still very much a work in progress!

## Configurable storage

You can configure hither to use S3 or the local filesystem for storage. See application.conf for more details of
the settings for each storage type.

### Running

Once you have configured the environment variables - a template for this can be found in conf/env.template - run the following command:

docker run --rm --name hither -p 9000:9000 --env-file {{path to environment variables}} wiredthing/hither

#### Example

For environment variables: 

    AWS_DEFAULT_REGION=
    AWS_ACCESS_KEY_ID=
    AWS_SECRET_ACCESS_KEY=
    S3_BUCKET_NAME=


Stored at ~/myHither.vars. Run the command: 

    docker run --rm --name hither -p 9000:9000 --env-file ~/myHither.vars wiredthing/hither
    
*Note that file storage is not fully functional yet!*
