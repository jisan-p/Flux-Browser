#!/usr/bin/env bash

sudo systemctl start docker

sudo docker compose down -v

sudo docker compose up -d postgres
