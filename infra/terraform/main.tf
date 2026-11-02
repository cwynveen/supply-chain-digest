provider "aws" {
  region = var.region
}

variable "region" {
  description = "AWS region for the ECR repository."
  type        = string
  default     = "us-east-1"
}

resource "aws_ecr_repository" "app" {
  name                 = "supply-chain-digest"
  image_tag_mutability = "IMMUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }
}

output "repository_url" {
  description = "Push images here."
  value       = aws_ecr_repository.app.repository_url
}
