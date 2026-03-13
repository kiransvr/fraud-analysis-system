from app.main import map_risk


def test_map_risk_low_boundary() -> None:
    assert map_risk(0.0) == "LOW"
    assert map_risk(0.39) == "LOW"


def test_map_risk_medium_boundary() -> None:
    assert map_risk(0.40) == "MEDIUM"
    assert map_risk(0.74) == "MEDIUM"


def test_map_risk_high_boundary() -> None:
    assert map_risk(0.75) == "HIGH"
    assert map_risk(0.99) == "HIGH"
