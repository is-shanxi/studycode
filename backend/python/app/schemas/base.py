"""Base model for internal contracts with camelCase JSON aliases."""

from pydantic import BaseModel, ConfigDict
from pydantic.alias_generators import to_camel


class InternalModel(BaseModel):
    """Accept snake_case attributes and emit/accept camelCase JSON."""

    model_config = ConfigDict(
        alias_generator=to_camel,
        populate_by_name=True,
        extra="forbid",
    )
