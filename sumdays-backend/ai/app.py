from dotenv import load_dotenv
import os
from pathlib import Path

load_dotenv(dotenv_path=Path(__file__).resolve().parent.parent / ".env")

from flask import Flask, jsonify
from services.analysis.routes import analysis_bp
from services.extract.routes import extract_bp
from services.merge.routes import merge_bp
from services.image.routes import image_bp
from services.stt.routes import stt_bp

# api ex) http://localhost:5001/{feature_group}/{feature_name}
app = Flask(__name__)
app.register_blueprint(analysis_bp)
app.register_blueprint(extract_bp)
app.register_blueprint(merge_bp)
app.register_blueprint(image_bp)
app.register_blueprint(stt_bp)

# test: GET http://localhost:5001/
@app.route('/', methods=['GET'])
def health_check():
    return jsonify({
        "status": "AI Server Operational", 
        "message": f"Flask AI server listening on port {os.getenv('AI_SERVER_PORT')}."
    }), 200


if __name__ == '__main__':
    print("--- Flask AI Server Starting ---")
    # # test python only
    # app.run(debug=True, port=os.getenv('AI_SERVER_PORT'))
    # with node.js
    app.run(debug=True, port=os.getenv('AI_SERVER_PORT'), host="0.0.0.0")
