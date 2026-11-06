#!/bin/sh
set -eu
[ "$(uname -s)-$(uname -m)" = Linux-x86_64 ] || { echo 'This installer supports Linux x86_64 only'; exit 1; }
mkdir -p .tools
curl --fail --location --retry 3 https://github.com/aquasecurity/trivy/releases/download/v0.76.2/trivy_0.76.2_Linux-64bit.tar.gz -o .tools/trivy.tar.gz
echo 'bc0fc726e017b8927a62b2d3531d7a84ee01769daf5c99ed866dced54089fdbf  .tools/trivy.tar.gz' | sha256sum -c -
tar -xzf .tools/trivy.tar.gz -C .tools trivy
curl --fail --location --retry 3 https://github.com/grafana/k6/releases/download/v2.3.0/k6-v2.3.0-linux-amd64.tar.gz -o .tools/k6.tar.gz
echo '39c3117b6af817592dcd0ce4242105c0a7af10948c2a425306f0be8f7a8a8ab1  .tools/k6.tar.gz' | sha256sum -c -
tar -xzf .tools/k6.tar.gz -C .tools
