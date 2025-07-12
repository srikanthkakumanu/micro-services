storage "file" {
  path="/vault/data"
}

listener "tcp" {
  address="0.0.0.0:8200"
  tls_disable=1 # Disables TLS for simplicity; in production, enable TLS with certificates.
}

ui=true
disable_mlock=true
