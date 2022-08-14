#!/bin/bash

#configure loopback for local development we need
# 127.0.0.1, 127.0.0.2 .... 127.0.0.X (X number of Akka nodes required for the Akka cluster)
sudo ifconfig lo0 alias 127.0.0.2 up # now we only need 2 nodes
sudo ifconfig lo0 alias 127.0.0.3 up
sudo ifconfig lo0 alias 127.0.0.4 up
