terraform {
  # 1.10 is the floor for use_lockfile (S3-native state locking, no DynamoDB table).
  required_version = ">= 1.10"

  # State lives in S3, not on a laptop. The bucket name embeds the account ID, which the
  # repo keeps out of committed files, so it is passed at init time instead (see README,
  # "Remote state"):
  #   terraform init -backend-config="bucket=fattorestreet-tfstate-<ACCOUNT_ID>"
  backend "s3" {
    key          = "hist-load/terraform.tfstate"
    region       = "us-east-1"
    encrypt      = true
    use_lockfile = true
  }

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = var.aws_region
}
