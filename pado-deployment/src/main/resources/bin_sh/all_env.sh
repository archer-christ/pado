#!/bin/bash

# ========================================================================
# Copyright (c) 2013-2015 Netcrest Technologies, LLC. All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
# ========================================================================

# 
# all_env.sh defines parameters to manage one or more grids,
# sites, and servers. Parameters defined here represent
# the top-level parameters which can be overwrriten in the
# lower-level parameter files found in each grid directory.
#

#
# System name represents all of the grids defined by $GRIDS.
#
SYSTEM_NAME=PADO

# GRIDS: List grid IDs separated by space. Each grid ID must have
# the corresponding directory with the same name that contains
# the grid specific environment files. A grid directory must contain
# the following files:
#    grid_env.sh - Defines grid-level parameters.
#    site_<site-id>.sh - Defines site-level parameters
#    bind_<site-id>.sh - Defines server-bind IP addresses.
#    locator_<site-id>.sh - Defines server-bind IP addresses.
#    server_<site-id>.sh - Defines server-bind IP addresses.

GRIDS="pi3"
#GRIDS="mygrid"
#GRIDS="mygrid_ssl"
#GRIDS="grid0 grid1 grid2"
#GRIDS="grid0 grid1 grid2 grid3 grid4 grid5"
#GRIDS="wan"
#GRIDS="wan_ssl"

# Enable or disable security. If security is disabled
# then the $ETC_GRID/gfsecurity.properties file is ignored.
# This parameter overrides the Pado property, "security.enabled".
# Note that if this parameter is set to true and the security
# file does not exist then the server will not start.
SECURITY_ENABLED=false
