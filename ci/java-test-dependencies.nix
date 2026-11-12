# Exact Maven Central artifacts for the retained Java tests.
{ fetchurl }: [
  (fetchurl { url = "https://repo.maven.apache.org/maven2/junit/junit/4.13.2/junit-4.13.2.jar"; sha256 = "8e495b634469d64fb8acfa3495a065cbacc8a0fff55ce1e31007be4c16dc57d3"; })
  (fetchurl { url = "https://repo.maven.apache.org/maven2/net/bytebuddy/byte-buddy/1.17.7/byte-buddy-1.17.7.jar"; sha256 = "3575dcb8a98faf943d3c1595c47a16047c4fce8a83ebbb26262f1a2f67546357"; })
  (fetchurl { url = "https://repo.maven.apache.org/maven2/net/bytebuddy/byte-buddy-agent/1.17.7/byte-buddy-agent-1.17.7.jar"; sha256 = "a9ba887dca252ad61b7d5153294f34e6f3bdf4b2736b04373d13615a695fc0ff"; })
  (fetchurl { url = "https://repo.maven.apache.org/maven2/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar"; sha256 = "66fdef91e9739348df7a096aa384a5685f4e875584cce89386a7a47251c4d8e9"; })
  (fetchurl { url = "https://repo.maven.apache.org/maven2/org/mockito/mockito-core/5.24.0/mockito-core-5.24.0.jar"; sha256 = "97a1f835ad84075255aa37809e4379eff541273c02c6201cbad49bd5950932cd"; })
  (fetchurl { url = "https://repo.maven.apache.org/maven2/org/objenesis/objenesis/3.3/objenesis-3.3.jar"; sha256 = "02dfd0b0439a5591e35b708ed2f5474eb0948f53abf74637e959b8e4ef69bfeb"; })
]
